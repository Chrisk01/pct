/**
 * Copyright 2005-2016 Riverside Software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.phenix.pct;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.types.Path;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Binary dump task
 * 
 * @author <a href="mailto:g.querret+PCT@gmail.com">Gilles QUERRET</a>
 * @version $Revision$
 */
public class PCTBinaryDump extends PCT {
    private List<PCTConnection> dbConnList = null;
    private List<Pattern> patterns = new ArrayList<Pattern>();
    private File dest = null;
    private Path propath = null;

    private int tblListID = -1;
    private File tblListFile = null;

    /**
     * Default constructor
     * 
     */
    public PCTBinaryDump() {
        this(true);
    }

    /**
     * Default constructor
     * 
     * @param tmp True if temporary files need to be created
     */
    public PCTBinaryDump(boolean tmp) {
        super();

        tblListID = PCT.nextRandomInt();
        tblListFile = new File(System.getProperty("java.io.tmpdir"), "tblList" + tblListID + ".txt"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Set the propath to be used when running the procedure
     * 
     * @param propath an Ant Path object containing the propath
     */
    public void setPropath(Path propath) {
        createPropath().append(propath);
    }

    /**
     * Creates a new Path instance
     * 
     * @return Path
     */
    public Path createPropath() {
        if (this.propath == null) {
            this.propath = new Path(this.getProject());
        }

        return this.propath;
    }

    /**
     * Adds a database connection
     * 
     * @param dbConn Instance of DBConnection class
     * @deprecated
     */
    public void addPCTConnection(PCTConnection dbConn) {
        addDBConnection(dbConn);
    }

    // Variation pour antlib
    public void addDB_Connection(PCTConnection dbConn) {
        addDBConnection(dbConn);
    }

    public void addDBConnection(PCTConnection dbConn) {
        if (this.dbConnList == null) {
            this.dbConnList = new ArrayList<PCTConnection>();
        }

        this.dbConnList.add(dbConn);
    }

    public void addConfiguredInclude(Pattern inc) {
        if (this.patterns == null) {
            this.patterns = new ArrayList<Pattern>();
        }
        inc.setInclude(true);
        this.patterns.add(inc);
    }

    public void addConfiguredExclude(Pattern exc) {
        if (this.patterns == null) {
            this.patterns = new ArrayList<Pattern>();
        }
        exc.setInclude(false);
        this.patterns.add(exc);
    }

    public void setDest(File dest) {
        this.dest = dest;
    }

    /**
     * Do the work
     * 
     * @throws BuildException Something went wrong
     */
    public void execute() throws BuildException {
        BufferedReader reader = null;

        checkDlcHome();
        if (dbConnList == null) {
            throw new BuildException(Messages.getString("PCTBinaryLoad.1")); //$NON-NLS-1$
        }

        if (dbConnList.size() > 1) {
            throw new BuildException(MessageFormat.format(
                    Messages.getString("PCTBinaryLoad.2"), "1")); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (dest == null) {
            throw new BuildException(Messages.getString("PCTBinaryDump.0")); //$NON-NLS-1$
        }

        try {
            PCTRun exec1 = getTables();
            exec1.execute();
        } catch (BuildException be) {
            cleanup();
            throw be;
        }

        String s = null;
        try {
            reader = new BufferedReader(new FileReader(tblListFile));
            while ((s = reader.readLine()) != null) {
                ExecTask exec2 = dumpTask(s);
                exec2.execute();
            }
            reader.close();
        } catch (IOException ioe) {
            try {
                reader.close();
            } catch (IOException ioe2) {
            }
            cleanup();
            throw new BuildException(Messages.getString("PCTBinaryDump.1")); //$NON-NLS-1$
        } catch (BuildException be) {
            try {
                reader.close();
            } catch (IOException ioe2) {
            }
            cleanup();
            throw be;
        }

        cleanup();
    }

    private ExecTask dumpTask(String table) throws BuildException {
        File executable = null;
        ExecTask exec = new ExecTask(this);

        if (getDLCMajorVersion() >= 10)
            executable = getExecPath("_dbutil"); //$NON-NLS-1$
        else
            executable = getExecPath("_proutil"); //$NON-NLS-1$

        Environment.Variable var = new Environment.Variable();
        var.setKey("DLC"); //$NON-NLS-1$
        var.setValue(getDlcHome().toString());
        exec.addEnv(var);

        exec.setExecutable(executable.toString());

        // Database connections
        for (PCTConnection dbc : dbConnList) {
            dbc.createArguments(exec);
        }

        // Binary dump
        exec.createArg().setValue("-C"); //$NON-NLS-1$
        exec.createArg().setValue("dump"); //$NON-NLS-1$

        // Table to dump
        exec.createArg().setValue(table);

        // Output directory
        exec.createArg().setValue(dest.getAbsolutePath());

        return exec;
    }

    private PCTRun getTables() {
        PCTRun exec = new PCTRun();
        exec.bindToOwner(this);
        exec.setDlcHome(getDlcHome());
        exec.setProcedure("pct/pctBinaryDump.p"); //$NON-NLS-1$
        exec.setGraphicalMode(false);
        exec.addPropath(propath);

        // Database connections
        for (PCTConnection dbc : dbConnList) {
            exec.addDBConnection(dbc);
        }

        StringBuffer sb = new StringBuffer();
        sb.append(tblListFile.getAbsolutePath());
        for (Pattern p : patterns) {
            StringBuffer sb2 = new StringBuffer();
            sb2.append('|');
            sb2.append((p.isInclude() ? 'I' : 'E'));
            sb2.append('$');
            sb2.append(p.getName());
            sb.append(sb2);
        }
        exec.setParameter(sb.toString());

        return exec;
    }

    /**
     * Delete temporary files if debug not activated
     * 
     */
    protected void cleanup() {
        if (tblListFile.exists() && !tblListFile.delete()) {
            log(MessageFormat.format(
                    Messages.getString("PCTBinaryDump.3"), tblListFile.getAbsolutePath()), Project.MSG_VERBOSE); //$NON-NLS-1$
        }
    }

    public static class Include extends Pattern {
    }
    public static class Exclude extends Pattern {
    }

}
