package com.github.lindenb.jvarkit.tools.misc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.github.lindenb.jvarkit.tools.tests.AlsoTest;
import com.github.lindenb.jvarkit.tools.tests.TestSupport;
import com.github.lindenb.jvarkit.util.bio.bed.BedLineCodecTest;
import com.github.lindenb.jvarkit.util.ucsc.KnownGeneTest;

@AlsoTest({KnownGeneTest.class,BedLineCodecTest.class})
public class KnownGenesToBedTest{
	private  final TestSupport support = new TestSupport();

@DataProvider(name = "src1")
public Object[][] createData1() {
	return new Object[][]{
		{support.resource("rotavirus_rf.knowngenes.tsv.gz")}
		};
	}
@Test(dataProvider="src1")
public void test(final String kgfile) throws IOException {
	try {
		Path out =support.createTmpPath(".bed");
		Assert.assertEquals(new KnownGenesToBed().instanceMain(new String[] {
				"-o",out.toString(),
				kgfile
				}),0);
		Assert.assertTrue(Files.exists(out));
		support.assertIsBed(out);
		} 
	finally {
		support.removeTmpFiles();
		}
	}
}
