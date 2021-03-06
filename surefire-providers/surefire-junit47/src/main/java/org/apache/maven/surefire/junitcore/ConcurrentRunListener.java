package org.apache.maven.surefire.junitcore;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Map;
import org.apache.maven.surefire.api.report.ConsoleOutputReceiver;
import org.apache.maven.surefire.api.report.ConsoleStream;
import org.apache.maven.surefire.api.report.ReportEntry;
import org.apache.maven.surefire.api.report.ReporterFactory;
import org.apache.maven.surefire.api.report.RunListener;
import org.apache.maven.surefire.api.report.RunMode;
import org.apache.maven.surefire.api.report.StackTraceWriter;
import org.apache.maven.surefire.api.report.TestSetReportEntry;
import org.apache.maven.surefire.api.testset.TestSetFailedException;

import static org.apache.maven.surefire.junitcore.TestMethod.getThreadTestMethod;

/**
 * Handles responses from concurrent junit
 * <br>
 * Stuff to remember about JUnit threading:
 * parallel=classes; beforeClass/afterClass, constructor and all tests method run on same thread
 * parallel=methods; beforeClass/afterClass run on main thread, constructor + each test method run on same thread
 * parallel=both; same as parallel=methods
 *
 * @see org.apache.maven.surefire.junitcore.JUnitCoreRunListener for details about regular junit run listening
 * @author Kristian Rosenvold
 */
public abstract class ConcurrentRunListener
    implements RunListener, ConsoleOutputReceiver
{
    private final Map<String, TestSet> classMethodCounts;

    private final ThreadLocal<RunListener> reporterManagerThreadLocal;

    private final boolean reportImmediately;

    private final ConsoleStream consoleStream;

    ConcurrentRunListener( final ReporterFactory reporterFactory, ConsoleStream consoleStream,
                           boolean reportImmediately, Map<String, TestSet> classMethodCounts )
        throws TestSetFailedException
    {
        this.reportImmediately = reportImmediately;
        this.classMethodCounts = classMethodCounts;
        this.consoleStream = consoleStream;
        reporterManagerThreadLocal = new ThreadLocal<RunListener>()
        {
            @Override
            protected RunListener initialValue()
            {
                return reporterFactory.createReporter();
            }
        };
    }

    @Override
    public void testSetStarting( TestSetReportEntry description )
    {
    }

    @Override
    public void testSetCompleted( TestSetReportEntry result )
    {
        try
        {
            final RunListener reporterManager = getRunListener();
            for ( TestSet testSet : classMethodCounts.values() )
            {
                testSet.replay( reporterManager );
            }
        }
        finally
        {
            reporterManagerThreadLocal.remove();
        }
    }

    @Override
    public void testFailed( ReportEntry failure )
    {
        final TestMethod testMethod = getOrCreateThreadAttachedTestMethod( failure );
        if ( testMethod != null )
        {
            testMethod.testFailure( failure );
            testMethod.detachFromCurrentThread();
        }
    }

    @Override
    public void testError( ReportEntry failure )
    {
        final TestMethod testMethod = getOrCreateThreadAttachedTestMethod( failure );
        if ( testMethod != null )
        {
            testMethod.testError( failure );
            testMethod.detachFromCurrentThread();
        }
    }

    @Override
    public void testSkipped( ReportEntry description )
    {
        TestSet testSet = getTestSet( description );
        TestMethod testMethod = testSet.createThreadAttachedTestMethod( description );
        testMethod.testIgnored( description );
        testSet.incrementFinishedTests( getRunListener(), reportImmediately );
        testMethod.detachFromCurrentThread();
    }

    @Override
    public void testExecutionSkippedByUser()
    {
        // cannot guarantee proper call to all listeners
        getRunListener().testExecutionSkippedByUser();
    }

    public RunMode markAs( RunMode currentRunMode )
    {
        return reporterManagerThreadLocal.get().markAs( currentRunMode );
    }

    @Override
    public void testAssumptionFailure( ReportEntry failure )
    {
        final TestMethod testMethod = getOrCreateThreadAttachedTestMethod( failure );
        if ( testMethod != null )
        {
            testMethod.testAssumption( failure );
            testMethod.detachFromCurrentThread();
        }
    }

    @Override
    public void testStarting( ReportEntry description )
    {
        TestSet testSet = getTestSet( description );
        testSet.createThreadAttachedTestMethod( description );

        checkIfTestSetCanBeReported( testSet );
        testSet.attachToThread();
    }

    @Override
    public void testSucceeded( ReportEntry report )
    {
        TestMethod testMethod = getThreadTestMethod();
        if ( testMethod != null )
        {
            testMethod.testFinished();
            testMethod.getTestSet().incrementFinishedTests( getRunListener(), reportImmediately );
            testMethod.detachFromCurrentThread();
        }
    }

    private TestMethod getOrCreateThreadAttachedTestMethod( ReportEntry description )
    {
        TestMethod threadTestMethod = getThreadTestMethod();
        if ( threadTestMethod != null )
        {
            return threadTestMethod;
        }
        TestSet testSet = getTestSet( description );
        if ( testSet == null )
        {
            consoleStream.println( description.getName() );
            StackTraceWriter writer = description.getStackTraceWriter();
            if ( writer != null )
            {
                consoleStream.println( writer.writeTraceToString() );
            }
            return null;
        }
        else
        {
            return testSet.createThreadAttachedTestMethod( description );
        }
    }

    protected abstract void checkIfTestSetCanBeReported( TestSet testSetForTest );

    private TestSet getTestSet( ReportEntry description )
    {
        return classMethodCounts.get( description.getSourceName() );
    }

    RunListener getRunListener()
    {
        return reporterManagerThreadLocal.get();
    }

    public static ConcurrentRunListener createInstance( Map<String, TestSet> classMethodCounts,
                                                            ReporterFactory reporterFactory,
                                                            boolean parallelClasses, boolean parallelBoth,
                                                            ConsoleStream consoleStream )
        throws TestSetFailedException
    {
        return parallelClasses
            ? new ClassesParallelRunListener( classMethodCounts, reporterFactory, consoleStream )
            : new MethodsParallelRunListener( classMethodCounts, reporterFactory, !parallelBoth, consoleStream );
    }


    @Override
    public void writeTestOutput( String output, boolean newLine, boolean stdout )
    {
        TestMethod threadTestMethod = getThreadTestMethod();
        if ( threadTestMethod != null )
        {
            LogicalStream logicalStream = threadTestMethod.getLogicalStream();
            logicalStream.write( stdout, output, newLine );
        }
        else
        {
            // Not able to associate output with any thread. Just dump to console
            consoleStream.println( output );
        }
    }
}
