package se.kth;

import java.io.*;
import org.junit.Test;

import se.kth.Filesystem.BuildStatus;

import org.junit.Before;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.io.FileUtils;

public class ContinuousIntegrationTest {
    static ContinuousIntegration ci = new ContinuousIntegration(".serverbuild");
    static Writer log = new StringWriter();

    @Before
    public void setUp(){
        try{
            ContinuousIntegration.cloneRepository(log, "https://github.com/Juliapp123/test.git", "main", ".serverbuild");
        }catch(Exception e){}
    }

    @Test
    public void cloneInvalidRepoTest(){
        assertThrows(Exception.class, () -> ContinuousIntegration.cloneRepository(log, "https://github.com/Juliapp123/test.git", "nonExisting", ".serverbuild"));
    }

    @Test
    public void compileInvalidRepoTest(){
        try{
        ContinuousIntegration.cloneRepository(log, "https://github.com/Juliapp123/test.git", "complieError", ".serverbuild");
        FileUtils.deleteDirectory(new File(".serverbuild"));
        }catch(Exception e){
            assertThrows(null, null);
        }
    }

    @Test
    public void compileRepoTest(){
        try{
            assertTrue(ci.compileProject(log));
        }catch(Exception e){
            System.out.println(e);
            fail();
        }
    }

    @Test
    public void testRepoTest(){
        try{
            assertTrue(ci.testProject(log));
        }catch(Exception e){
            System.out.println(e);
            fail();
        }
    }


    @Test
    public void testRepoInvalidTest(){
        try{
            ContinuousIntegration.cloneRepository(log, "https://github.com/Juliapp123/test.git", "Fail", ".serverbuild");
            assertFalse(ci.testProject(log));
        }catch(Exception e){
            System.out.println(e);
            fail();
        }
    }

    @Test
    public void testNotificationTest(){
        try{
            assertTrue(ContinuousIntegrationServer.sendResponse("34153878c185d9f5f695de49527ae1397dc644ba", "Juliapp123", "test", BuildStatus.SUCCESS));
        }catch(Exception e){
            System.out.println(e);
            fail();
        }
    }

    @Test
    public void testNotificationInvalidTest(){
        // Invalid SHA (commitId)
        try{
            ContinuousIntegrationServer.sendResponse("34153878c185d9f5f695de49527ae1397dc44ba", "Juliapp123", "test", BuildStatus.SUCCESS);
        }catch(Exception e){
            assertThrows(null, null, null);
            System.out.println(e);
            fail();
        }
    }
}
