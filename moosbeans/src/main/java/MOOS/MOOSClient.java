/*   MOOS - Mission Oriented Operating Suite 
*  
*   A suit of Applications and Libraries for Mobile Robotics Research 
*   Copyright (C) 2001-2005 Massachusetts Institute of Technology and 
*   Oxford University. 
*
*   The original C++ version of this software was written by Paul Newman
*   at MIT 2001-2002 and Oxford University 2003-2005. 
*   email: pnewman@robots.ox.ac.uk. 
*	  
*   This Java version of MOOSClient is part of the MOOSBeans for Java
*   package written by Benjamin C. Davis at Oxford University 2010-2011  
*   email: ben@robots.ox.ac.uk
*
*   This file is part of the MOOSBeans for Java package.
*		
*   This program is free software; you can redistribute it and/or 
*   modify it under the terms of the GNU General Public License as 
*   published by the Free Software Foundation; either version 2 of the 
*   License, or (at your option) any later version. 
*		  
*   This program is distributed in the hope that it will be useful, 
*   but WITHOUT ANY WARRANTY; without even the implied warranty of 
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
*   General Public License for more details. 
*			
*   You should have received a copy of the GNU General Public License 
*   along with this program; if not, write to the Free Software 
*   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 
*   02111-1307, USA. 
*
*                      END_GPL   
*/

package MOOS;

import java.io.Serializable;
import java.util.Arrays;

/**
 * This class is an implementation of MOOSClientInterface which can either be
 * extended or used as a template for your own classes.
 * 
 * @author Benjamin C. Davis
 */
public class MOOSClient implements MOOSClientInterface, Serializable {

    public String[] varNames = {""}; // this can be overridden or altered by the subclass.
    public Double[] msgIntervals = {200.0d};
    protected MOOSEventServer mOOSCommClient;

    public Iterable<String> getVariableNames() {
        return Arrays.asList(varNames);
    }

    public Iterable<Double> getRequiredMsgIntervals() {
        return Arrays.asList(msgIntervals);
    }

    public void setMOOSCommClient(MOOSEventServer client) {
        this.mOOSCommClient = client;
    }

    public MOOSEventServer getMOOSCommClient() {
        return mOOSCommClient;
    }
/**
 * override me
 * @param messages
 */
    public void processMOOSMsg(Iterable<MOOSMsg> messages) {
        // here we handle the messages.
        // messages shoud be in order of most recent first
        for (MOOSMsg m : messages) {
            System.out.println("We got a message:");
            m.trace();
        }
    }
}
