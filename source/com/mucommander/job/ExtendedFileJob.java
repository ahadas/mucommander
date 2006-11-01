
package com.mucommander.job;

import com.mucommander.file.AbstractFile;
import com.mucommander.file.FileSet;
import com.mucommander.io.ByteCounter;
import com.mucommander.io.CounterInputStream;
import com.mucommander.io.FileTransferException;
import com.mucommander.io.ThroughputLimitInputStream;
import com.mucommander.text.Translator;
import com.mucommander.ui.MainFrame;
import com.mucommander.ui.ProgressDialog;
import com.mucommander.Debug;

import java.io.IOException;
import java.io.InputStream;


/**
 * ExtendedFileJob is a container for a file task : basically an operation that involves files and bytes.<br>
 *
 * <p>What makes ExtendedFileJob different from FileJob (and explains its very inspired name) is that a class
 * implementing ExtendedFileJob has to be able to give progress information about the file currently being processed.
 * 
 * @author Maxence Bernard
 */
public abstract class ExtendedFileJob extends FileJob {

    /** Contains the number of bytes processed in the current file so far, see {@link #getCurrentFileByteCounter()} ()} */
    private ByteCounter currentFileByteCounter;

    /** Contains the number of bytes processed so far, see {@link #getTotalByteCounter()} */
    private ByteCounter totalByteCounter;


    /** InputStream currently being processed, may be null */
    private ThroughputLimitInputStream in;

    /** ThroughputLimit in bytes per second, -1 initially (no limit) */
    private long throughputLimit = -1;



    /**
     * Creates a new ExtendedFileJob.
     */
    public ExtendedFileJob(ProgressDialog progressDialog, MainFrame mainFrame, FileSet files) {
        super(progressDialog, mainFrame, files);

        this.currentFileByteCounter = new ByteCounter();

        // Account the current file's byte counter in the total byte counter
        this.totalByteCounter = new ByteCounter(currentFileByteCounter);
    }

	
    /**
     * Copies the given source file to the specified destination file, optionally resuming the operation.
     */
    protected void copyFile(AbstractFile sourceFile, AbstractFile destFile, boolean append) throws FileTransferException {
        // Determine whether AbstractFile.copyTo() should be used to copy file or streams should be copied manually.
        // Some file protocols do not provide a getOutputStream() method and require the use of copyTo(). Some other
        // may also offer server to server copy which is more efficient than stream copy.
        int copyToHint = sourceFile.getCopyToHint(destFile);

        // copyTo() should or must be used
        if(copyToHint==AbstractFile.SHOULD_HINT || copyToHint==AbstractFile.MUST_HINT) {
            if(com.mucommander.Debug.ON) com.mucommander.Debug.trace("calling copyTo()");
            sourceFile.copyTo(destFile);
        }

        // Copy source file stream to destination file
        try {
            // Try to open InputStream
            try  {
                long destFileSize = destFile.getSize();
        
                if(append && destFileSize!=-1) {
//                    this.in = new CounterInputStream(sourceFile.getInputStream(destFileSize), currentFileByteCounter);
                    setCurrentInputStream(sourceFile.getInputStream(destFileSize));
                    currentFileByteCounter.add(destFileSize);
                }
                else {
                    setCurrentInputStream(sourceFile.getInputStream());
//                    this.in = new CounterInputStream(sourceFile.getInputStream(), currentFileByteCounter);
                }
            }
            catch(IOException e) {
                if(com.mucommander.Debug.ON) {
                    com.mucommander.Debug.trace("IOException caught: "+e+", throwing FileTransferException");
                    e.printStackTrace();
                }
                throw new FileTransferException(FileTransferException.OPENING_SOURCE);
            }
    
            // Copy source stream to destination file
            destFile.copyStream(in, append);
        }
        finally {
            // This block will always be executed, even if an exception
            // was thrown in the catch block

            // Tries to close the streams no matter what happened before
            if(in!=null) {
                try { in.close(); }
                catch(IOException e1) {}
            }
        }
    }


    /**
     * Tries to copy the given source file to the specified destination file (see {@link #copyFile(AbstractFile,AbstractFile,boolean)}
     * displaying a generic error dialog {@link #showErrorDialog(String, String) #showErrorDialog()} if something went wrong, 
     * and giving the user the choice to skip the file, retry or cancel.
     *
     * @return true if the file was properly copied, false if the transfer was interrupted / aborted by the user
     *
     */
    protected boolean tryCopyFile(AbstractFile sourceFile, AbstractFile destFile, boolean append, String errorDialogTitle) {
        // Copy file to destination
        do {				// Loop for retry
            try {
                copyFile(sourceFile, destFile, append);
                return true;
            }
            catch(FileTransferException e) {
                // If job was interrupted by the user at the time when the exception occurred,
                // it most likely means that the exception by user cancellation.
                // In this case, the exception should not be interpreted as an error.
                if(isInterrupted())
                    return false;

                // Copy failed
                if(com.mucommander.Debug.ON) {
                    com.mucommander.Debug.trace("Copy failed: "+e);
                    e.printStackTrace();
                }

                int reason = e.getReason();
                int choice;
                switch(reason) {
                    // Could not open source file for read
                case FileTransferException.OPENING_SOURCE:
                    // Ask the user what to do
                    choice = showErrorDialog(errorDialogTitle, Translator.get("cannot_read_file", sourceFile.getName()));
                    break;
                    // Could not open destination file for write
                case FileTransferException.OPENING_DESTINATION:
                    choice = showErrorDialog(errorDialogTitle, Translator.get("cannot_write_file", sourceFile.getName()));
                    break;
                    // An error occurred during file transfer
//                case FileTransferException.ERROR_WHILE_TRANSFERRING:
                default:
                    choice = showErrorDialog(errorDialogTitle, 
                                             Translator.get("error_while_transferring", sourceFile.getName()),
                                             new String[]{SKIP_TEXT, APPEND_TEXT, RETRY_TEXT, CANCEL_TEXT},
                                             new int[]{SKIP_ACTION, APPEND_ACTION, RETRY_ACTION, CANCEL_ACTION}
                                             );
                    break;
                }
				
                // cancel action or close dialog
                if(choice==-1 || choice==CANCEL_ACTION) {
                    stop();
                    return false;
                }
                else if(choice==SKIP_ACTION) { 	// skip
                    return false;
                }
                // Retry action (append or retry)
                else {
//                    if(reason==FileTransferException.ERROR_WHILE_TRANSFERRING) {
                        // Reset processed bytes currentFileByteCounter
                        currentFileByteCounter.reset();
                        // Append resumes transfer
                        append = choice==APPEND_ACTION;
//                    }
                    continue;
                }
            }
        } while(true);
    }
	
    
    /**
     * Returns the percentage of the current file which has been processed, or 0 if current file's size is not available
     * (in this case getNbCurrentFileBytesProcessed() returns -1).
     */
    public float getFilePercentDone() {
        long currentFileSize = getCurrentFileSize();
        if(currentFileSize<=0)
            return 0;
        else
//            return (int)(100*getCurrentFileByteCounter().getByteCount()/(float)currentFileSize);
            return getCurrentFileByteCounter().getByteCount()/(float)currentFileSize;
    }


    /**
     * Returns the number of bytes that have been processed in the current file.
     */
    public ByteCounter getCurrentFileByteCounter() {
        return currentFileByteCounter;
    }


    /**
     * Returns the size of the file currently being processed, -1 if is not available.
     */
    public long getCurrentFileSize() {
        return currentFile==null?-1:currentFile.getSize();
    }


    /**
     * Returns a {@link ByteCounter} that holds the total number of bytes that have been processed by this job so far.
     */
    public ByteCounter getTotalByteCounter() {
        return totalByteCounter;
    }


    /**
     * Registers the given InputStream as currently used, to allow:
     * <ul>
     * <li>counting bytes that have been read from it (@see {@link #getCurrentFileByteCounter()}
     * <li>blocking read methods calls when the job is paused
     * <li>closing the InputStream when job is stopped
     * </ul>
     *
     * <p>This method should be called by subclasses when creating a new InputStream, before the InputStream is used.
     *  
     * @param in the InputStream to be used
     * @return the 'augmented' InputStream using the given stream as the underlying InputStream
     */
    public InputStream setCurrentInputStream(InputStream in) {
        this.in = new ThroughputLimitInputStream(new CounterInputStream(in, currentFileByteCounter), throughputLimit);

        return this.in;
    }


    public void setThroughputLimit(long bytesPerSecond) {
        this.throughputLimit = bytesPerSecond;

        if(in!=null)
            in.setThroughputLimit(throughputLimit);
    }

    public long getThroughputLimit() {
        return throughputLimit;
    }
    

    ////////////////////////
    // Overridden methods //
    ////////////////////////


    /**
     * Overrides {@link FileJob#jobStopped()} to stop any file processing by closing the source InputStream.
     */
    protected void jobStopped() {
        super.jobStopped();

        if(in!=null) {
            if(Debug.ON) Debug.trace("closing current InputStream "+in);

            try { in.close(); }
            catch(IOException e) {}
        }
    }

    /**
     * Overrides {@link FileJob#jobPaused()} to pause any file processing
     * by having the source InputStream's read methods lock.
     */
    protected void jobPaused() {
        super.jobPaused();

        if(in!=null)
            in.setThroughputLimit(0);
    }

    /**
     * Overrides {@link FileJob#jobResumed()} to resume any file processing by releasing
     * the lock on the source InputStream's read methods.
     */
    protected void jobResumed() {
        super.jobResumed();

        if(in!=null)
            in.setThroughputLimit(-1);
    }

//    /**
//     * Overrides FileJob.stop() to stop any file copy (closes the source file's InputStream).
//     */
//    public void stop() {
//        // Stop job BEFORE closing the stream so that the IOException thrown by copyStream
//        // is not interpreted as a failure
//        super.stop();
//
//        if(in!=null) {
//            try {
//                in.close();
//            }
//            catch(IOException e) {}
//        }
//    }


    /**
     * Advances file index and resets file bytes currentFileByteCounter. This method should be called by subclasses whenever the job
     * starts processing a new file.
     */
    protected void nextFile(AbstractFile file) {
        totalByteCounter.add(currentFileByteCounter.getByteCount());
        currentFileByteCounter.reset();

        super.nextFile(file);
    }


    /**
     * Method overridden to return a more accurate percentage of job processed so far by taking
     * into account the current file's processed percentage.
     */
    public float getTotalPercentDone() {
        float nbFilesProcessed = getCurrentFileIndex();
		
        // If file is in base folder and is not a directory
        if(currentFile!=null && files.indexOf(currentFile)!=-1 && !currentFile.isDirectory()) {
            // Take into account current file's progress
            long currentFileSize = currentFile.getSize();
            if(currentFileSize>0)
                nbFilesProcessed += getCurrentFileByteCounter().getByteCount()/(float)currentFileSize;
        }
			
//        return (int)(100*(nbFilesProcessed/(float)getNbFiles()));
        return nbFilesProcessed/(float)getNbFiles();
    }
    
}
