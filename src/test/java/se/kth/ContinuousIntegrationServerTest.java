package se.kth;

import java.io.*;
import org.junit.Test;
import org.junit.Before;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringWriter;

import javax.swing.text.TableView.TableRow;

import org.apache.commons.io.FileUtils;

public class ContinuousIntegrationServerTest {
    static Writer log = new StringWriter();

    @Before
    public void setUp(){
        try{
            ContinuousIntegration.cloneRepository(log, "https://github.com/Juliapp123/test.git", "Fail", ".serverbuild");
        }catch(Exception e){}
    }

    @Test
    public void cloneInvalidRepoTest(){
        assertThrows(Exception.class, () -> ContinuousIntegration.cloneRepository(log, "https://github.com/Juliapp123/test.git", "nonExisting", ".serverbuild"));
    }

    @Test
    public void compileInvalidRepoTest(){
        try{
        FileUtils.deleteDirectory(new File(".serverbuild"));
        }catch(Exception e){}

        assertThrows(Exception.class, () -> ContinuousIntegration.compileProject(log));
    }

    @Test
    public void compileRepoTest(){
        try{
            assertTrue(ContinuousIntegration.compileProject(log));
        }catch(Exception e){
            System.out.println(e);
            fail();
            
        }
    }
}
