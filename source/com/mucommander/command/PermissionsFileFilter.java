/*
 * This file is part of muCommander, http://www.mucommander.com
 * Copyright (C) 2002-2009 Maxence Bernard
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mucommander.command;

import com.mucommander.file.AbstractFile;
import com.mucommander.file.PermissionAccesses;
import com.mucommander.file.PermissionTypes;
import com.mucommander.file.filter.FileFilter;
import com.mucommander.runtime.JavaVersions;

/**
 * Filter on a file's permissions.
 * @author Nicolas Rinaudo
 */
public class PermissionsFileFilter extends FileFilter implements PermissionTypes, PermissionAccesses {
    private int     permission;
    private boolean filter;

    /**
     * Creates a new <code>PermissionsFileFilter</code>.
     * @param permission permission that will be checked against as defined in {@link com.mucommander.file.FilePermissions}.
     * @param filter     whether or not the specified permission flag must be set for a file to be accepted.
     */
    public PermissionsFileFilter(int permission, boolean filter) {
        this.permission = permission;
        this.filter     = filter;
    }

    @Override
    public boolean accept(AbstractFile file) {
        if(permission== EXECUTE_PERMISSION && JavaVersions.JAVA_1_5.isCurrentOrLower())
            return true;

        return filter ? file.getPermissions().getBitValue(USER_ACCESS, permission) : !file.getPermissions().getBitValue(USER_ACCESS, permission);
    }

    /**
     * Returns the permission that this filter will check.
     * @return the permission that this filter will check.
     */
    public int getPermission() {return permission;}

    /**
     * Returns <code>true</code> if files must have the filter's permission flag set in order to be accepted.
     * @return <code>true</code> if files must have the filter's permission flag set in order to be accepted, <code>false</code> otherwise.
     */
    public boolean getFilter() {return filter;}
}

