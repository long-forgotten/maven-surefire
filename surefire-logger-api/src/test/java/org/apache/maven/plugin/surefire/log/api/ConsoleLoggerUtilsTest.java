package org.apache.maven.plugin.surefire.log.api;

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

import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link ConsoleLoggerUtils}.
 */
public class ConsoleLoggerUtilsTest
{
    @Test
    public void testMacOS() throws Exception
    {
        System.out.println( "localhost: " + InetAddress.getLocalHost() );
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        while ( nets.hasMoreElements() )
        {
            NetworkInterface net = nets.nextElement();
            if ( !net.isUp() )
            {
                continue;
            }
            System.out.println( "NetworkInterface: " + net.toString() + ", addresses=" + net.getInetAddresses()
                + ", up=" + net.isUp() + ", virtual=" + net.isVirtual() + ", loopback=" + net.isLoopback() );
            System.out.println( "InetAddresses:" );
            for ( Enumeration<InetAddress> inets = net.getInetAddresses(); inets.hasMoreElements();  )
            {
                InetAddress addr = inets.nextElement();
                System.out.println( addr
                    + " (hostname = " + addr.getHostName()
                    + ", cannonical hostname=" + addr.getCanonicalHostName()
                    + ", anylocalAddr=" + addr.isAnyLocalAddress()
                    + ", loopback=" + addr.isLoopbackAddress()
                    + ", linkLocalAddr=" + addr.isLinkLocalAddress()
                    + ", siteLocalAddr=" + addr.isSiteLocalAddress()
                    + ")" );
            }
        }

        byte[] content = Files.readAllBytes( Paths.get( "/etc", "hosts" ) );
        System.out.println( "The content of '/etc/hosts'" );
        System.out.println( new String( content ) );

    }

    @Test
    public void shouldPrintStacktraceAsString()
    {
        Exception e = new IllegalArgumentException( "wrong param" );
        String msg = ConsoleLoggerUtils.toString( e );

        StringWriter text = new StringWriter();
        PrintWriter writer = new PrintWriter( text );
        e.printStackTrace( writer );
        String s = text.toString();

        assertThat( msg )
                .isEqualTo( s );
    }

    @Test
    public void shouldPrintStacktracWithMessageAsString()
    {
        Exception e = new IllegalArgumentException( "wrong param" );
        String msg = ConsoleLoggerUtils.toString( "issue", e );

        StringWriter text = new StringWriter();
        PrintWriter writer = new PrintWriter( text );
        writer.println( "issue" );
        e.printStackTrace( writer );
        String s = text.toString();

        assertThat( msg )
                .isEqualTo( s );
    }
}
