package edu.berkeley.cs162.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.berkeley.cs162.KVCache;
import edu.berkeley.cs162.KVException;
import edu.berkeley.cs162.KVServer;
import edu.berkeley.cs162.KVStore;

/**
 * @author malienalguy
 */

public class TPCMasterHandlerTest {

  private KVServer kvServer;
  private TPCMasterHandler masterHandler;

  @Before
  public void setUp(){
    kvServer = new KVServer(4, 20);
    masterHandler = new TPCMasterHandler(kvServer);
    masterHandler.setTPCLog(new TPCLog("log", kvServer));
  }

  @After
  public void tearDown(){
    kvServer = null;
    masterHandler = null;
  }

  @Test
  public testGet() throws Exception{
  }
}
