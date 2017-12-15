/*
The MIT License (MIT)

Copyright (c) 2017 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

*/
package com.github.lindenb.jvarkit.tools.sam2tsv;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.beust.jcommander.Parameter;
import com.github.lindenb.jvarkit.tools.misc.IlluminaReadName;
import com.github.lindenb.jvarkit.util.jcommander.Launcher;
import com.github.lindenb.jvarkit.util.jcommander.Program;
import com.github.lindenb.jvarkit.util.log.Logger;
import com.github.lindenb.jvarkit.util.picard.GenomicSequence;
import com.github.lindenb.jvarkit.util.swing.ColorUtils;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFlag;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.ProgressLoggerInterface;
/**
BEGIN_DOC

## Example

```

$ java -jar dist/prettysam.jar -R ref.fa S1.bam

>>>>> 1
          Read-Name : rotavirus_1_317_5:0:0_7:0:0_2de
               Flag : 99
             read paired : 1
             proper pair : 2
     mate reverse strand : 32
           first of pair : 64
               MAPQ : 60
             Contig : rotavirus  (index:0)
              Start : 1
                End : 70
             Strand : +
        Insert-Size : 317
        Mate-Contig : rotavirus  (index:0)
         Mate-Start : 248
        Mate-Strand : -
         Read Group : 
                      ID : S1
                      SM : S1
        Read-Length : 70
              Cigar : 70M (N=1)
           Sequence : 
                Read (0) : GGCTTTTAAT GCTTTTCAGT GGTTGCTGCT CAATATGGCG TCAACTCAGC AGATGGTCAG
                     Mid : |||||||||| |||||||||| |||||||||| ||| |||| | || ||||||| ||||||| ||
                 Ref (1) : GGCTTTTAAT GCTTTTCAGT GGTTGCTGCT CAAGATGGAG TCTACTCAGC AGATGGTAAG
                      Op : MMMMMMMMMM MMMMMMMMMM MMMMMMMMMM MMMMMMMMMM MMMMMMMMMM MMMMMMMMMM
                    Qual : ++++++++++ ++++++++++ ++++++++++ ++++++++++ ++++++++++ ++++++++++
                 Ref-Pos : 1          11         21         31         41         51        

               Read (60) : CTCTAATATT
                     Mid : ||||| ||||
                Ref (61) : CTCTATTATT
                      Op : MMMMMMMMMM
                    Qual : ++++++++++
                 Ref-Pos : 61        

               Tags : 
                      MD :  33G4A3T14A7T4   "String for mismatching positions"
                      NM :  5   "Edit distance to the reference"
                      AS :  45   "Alignment score generated by aligner"
                      XS :  0   "Reserved for end users"
<<<<< 1

>>>>> 2
          Read-Name : rotavirus_1_535_4:0:0_4:0:0_1a6
               Flag : 163
             read paired : 1
             proper pair : 2
     mate reverse strand : 32
          second of pair : 128
               MAPQ : 60
             Contig : rotavirus  (index:0)
              Start : 1
                End : 70
             Strand : +
        Insert-Size : 535
        Mate-Contig : rotavirus  (index:0)
         Mate-Start : 466
        Mate-Strand : -
         Read Group : 
                      ID : S1
                      SM : S1
        Read-Length : 70
              Cigar : 70M (N=1)
           Sequence : 
                Read (0) : GGCTTTTACT GCTTTTCAGT GGTTGCTTCT CAAGATGGAG TGTACTCATC AGATGGTAAG
                     Mid : |||||||| | |||||||||| ||||||| || |||||||||| | |||||| | ||||||||||
                 Ref (1) : GGCTTTTAAT GCTTTTCAGT GGTTGCTGCT CAAGATGGAG TCTACTCAGC AGATGGTAAG
                      Op : MMMMMMMMMM MMMMMMMMMM MMMMMMMMMM MMMMMMMMMM MMMMMMMMMM MMMMMMMMMM
                    Qual : ++++++++++ ++++++++++ ++++++++++ ++++++++++ ++++++++++ ++++++++++
                 Ref-Pos : 1          11         21         31         41         51        

               Read (60) : CTCTATTATT
                     Mid : ||||||||||
                Ref (61) : CTCTATTATT
                      Op : MMMMMMMMMM
                    Qual : ++++++++++
                 Ref-Pos : 61        

               Tags : 
                      MD :  8A18G13C6G21   "String for mismatching positions"
                      NM :  4   "Edit distance to the reference"
                      AS :  50   "Alignment score generated by aligner"
                      XS :  0   "Reserved for end users"
<<<<< 2
```

END_DOC
 */
@Program(name="prettysam",
description="Pretty SAM alignments",
keywords={"sam","bam",}
)
public class PrettySam extends Launcher {
	private static final Logger LOG = Logger.build(PrettySam.class).make();
	@Parameter(names={"-o","--output"},description=OPT_OUPUT_FILE_OR_STDOUT)
	private File outputFile = null;
	@Parameter(names={"-r","-R","--reference"},description=INDEXED_FASTA_REFERENCE_DESCRIPTION)
	private File refFile = null;
	
	public static class PrettySAMWriter implements SAMFileWriter
		{
	    final Pattern SEMICOLON_PAT = Pattern.compile("[;]");
	    final Pattern COMMA_PAT = Pattern.compile("[,]");

		private final NumberFormat fmt = new DecimalFormat("#,###");
		private final PrintWriter pw ;
		private SAMFileHeader header = null;
		private long nLine=0;
		private IndexedFastaSequenceFile indexedFastaSequenceFile=null;
		private GenomicSequence genomicSequence=null;
		private final Map<String,String> tags = new HashMap<>();
		
		public PrettySAMWriter(PrintWriter pw) {
			this.pw = pw;
			
			tags.put("AM","The smallest template-independent mapping quality of segments in the rest");
			tags.put("AS","Alignment score generated by aligner");
			tags.put("BC","Barcode sequence identifying the sample");
			tags.put("BQ","Offset to base alignment quality (BAQ)");
			tags.put("BZ","Phred quality of the unique molecular barcode bases in the {\tt OX} tag");
			tags.put("CC","Reference name of the next hit");
			tags.put("CG","& BAM only: CIGAR in BAM's binary encoding if (and only if) it consists of $>$65535 operators");
			tags.put("CM","Edit distance between the color sequence and the color reference (see also {\tt NM})");
			tags.put("CO","Free-text comments");
			tags.put("CP","Leftmost coordinate of the next hit");
			tags.put("CQ","Color read base qualities");
			tags.put("CS","Color read sequence");
			tags.put("CT","Complete read annotation tag, used for consensus annotation dummy features");
			tags.put("E2","The 2nd most likely base calls");
			tags.put("FI","The index of segment in the template");
			tags.put("FS","Segment suffix");
			tags.put("FZ","& Flow signal intensities");
			tags.put("GC","Reserved for backwards compatibility reasons");
			tags.put("GQ","Reserved for backwards compatibility reasons");
			tags.put("GS","Reserved for backwards compatibility reasons");
			tags.put("H0","Number of perfect hits");
			tags.put("H1","Number of 1-difference hits (see also {\tt NM})");
			tags.put("H2","Number of 2-difference hits");
			tags.put("HI","Query hit index");
			tags.put("IH","Number of stored alignments in SAM that contains the query in the current record");
			tags.put("LB","Library");
			tags.put("MC","CIGAR string for mate/next segment");
			tags.put("MD","String for mismatching positions");
			tags.put("MF","Reserved for backwards compatibility reasons");
			tags.put("MI","Molecular identifier; a string that uniquely identifies the molecule from which the record was derived");
			tags.put("MQ","Mapping quality of the mate/next segment");
			tags.put("NH","Number of reported alignments that contains the query in the current record");
			tags.put("NM","Edit distance to the reference");
			tags.put("OC","Original CIGAR");
			tags.put("OP","Original mapping position");
			tags.put("OQ","Original base quality");
			tags.put("OX","Original unique molecular barcode bases");
			tags.put("PG","Program");
			tags.put("PQ","Phred likelihood of the template");
			tags.put("PT","Read annotations for parts of the padded read sequence");
			tags.put("PU","Platform unit");
			tags.put("Q2","Phred quality of the mate/next segment sequence in the R2 tag");
			tags.put("QT","Phred quality of the sample-barcode sequence in the BC or RT tag");
			tags.put("QX","Quality score of the unique molecular identifier in the RX tag");
			tags.put("R2","Sequence of the mate/next segment in the template");
			tags.put("RG","Read group");
			tags.put("RT","Barcode sequence (deprecated; use BC instead)");
			tags.put("RX","Sequence bases of the (possibly corrected) unique molecular identifier");
			tags.put("SA","Other canonical alignments in a chimeric alignment");
			tags.put("SM","Template-independent mapping quality");
			tags.put("SQ","Reserved for backwards compatibility reasons");
			tags.put("S2","Reserved for backwards compatibility reasons");
			tags.put("TC","The number of segments in the template");
			tags.put("U2","Phred prob. of the 2nd call being wrong conditional on the best being wrong");
			tags.put("UQ","Phred likelihood of the segment, conditional on the mapping being correct");
			tags.put("X?","Reserved for end users");
			tags.put("Y?","Reserved for end users");
			tags.put("Z?","Reserved for end users");			
			}
		
		private String getTagDescription(final String s) {
			if(s.equals(ColorUtils.YC_TAG)) {
				return "IGV Color tag";
				}
			if(s.startsWith("X") || s.startsWith("Y") || s.startsWith("Z"))
				{
				return "Reserved for end users";
				}
			String v = this.tags.get(s);
			return v==null?"Unknown":v;
		}
		
		private static class Base
			{
			char readbase;
			char readqual;
			int  readpos;
			char refbase;
			int  refpos;
			CigarOperator cigaroperator;
			}

		
		public PrettySAMWriter setReferenceFile(final File f) throws IOException{
			if(f==null) return null;
			CloserUtil.close(this.indexedFastaSequenceFile);
			this.genomicSequence=null;
			this.indexedFastaSequenceFile=new IndexedFastaSequenceFile(f);
			return this;
			}
		
		private char getReferenceAt(final String contig,int refpos) {
			if(this.indexedFastaSequenceFile==null) return 'N';
			
			if(this.genomicSequence==null || !this.genomicSequence.getChrom().equals(contig))
				{
				this.genomicSequence = new GenomicSequence(this.indexedFastaSequenceFile, contig);
				}
			if((refpos-1)>=this.genomicSequence.length())return 'N';
			return this.genomicSequence.charAt(refpos-1);
			}
		
		@Override
		public void setProgressLogger(ProgressLoggerInterface progress) {
			
			}
		@Override
		public SAMFileHeader getFileHeader() {
			return header;
		}	
		
		public void writeHeader(final SAMFileHeader header) {
			this.header = header;
			pw.flush();
			}
		
		private void label(int cols,final String s) { pw.printf("%"+cols+"s : ",s); }
		
		@Override
		public void addAlignment(final SAMRecord rec) {
			final int margin1=19;
			final int margin2=margin1+5;
			
			++this.nLine;
			if(this.nLine>1) pw.println();
			pw.println(">>>>> "+nLine);
			label(margin1,"Read-Name");pw.println(rec.getReadName());
			
			 new IlluminaReadName.Parser().apply(rec.getReadName()).
			 	ifPresent(ilmn->{
					label(margin2,"Instrument");pw.println(ilmn.getInstrument());
					label(margin2,"Run");pw.println(ilmn.getRunId());
					label(margin2,"FlowCell");pw.println(ilmn.getFlowCell());
					label(margin2,"Lane");pw.println(ilmn.getLane());
					label(margin2,"Tile");pw.println(ilmn.getTile());
					label(margin2,"X");pw.println(ilmn.getX());
					label(margin2,"Y");pw.println(ilmn.getY());
					});
			label(margin1,"Flag");pw.println(rec.getFlags());
			for(final SAMFlag flg:SAMFlag.values())
				{
				if(!flg.isSet(rec.getFlags())) continue;
				label(margin2,flg.getLabel());pw.println(flg.intValue());
				}
			if(!rec.getReadUnmappedFlag()) {
				label(margin1,"MAPQ");
				pw.print(rec.getMappingQuality());
				if(rec.getMappingQuality()==SAMRecord.UNKNOWN_MAPPING_QUALITY)
					{
					pw.print(" (unknown)");
					}
				pw.println();
				
				label(margin1,"Contig");pw.println(rec.getReferenceName()+"  (index:"+rec.getReferenceIndex()+")");
				if(rec.getUnclippedStart()!=rec.getStart())
					{
					label(margin1,"Unclipped-Start");pw.println(this.fmt.format(rec.getUnclippedStart()));
					}
				label(margin1,"Start");pw.println(this.fmt.format(rec.getAlignmentStart()));
				label(margin1,"End");pw.println(this.fmt.format(rec.getAlignmentEnd()));
				if(rec.getUnclippedEnd()!=rec.getEnd())
					{
					label(margin1,"Unclipped-End");pw.println(this.fmt.format(rec.getUnclippedEnd()));
					}
				label(margin1,"Strand");pw.println(rec.getReadNegativeStrandFlag()?"-":"+");
				}
			if(rec.getReadPairedFlag() && !rec.getMateUnmappedFlag() && rec.getMateReferenceIndex()>=0)
				{
				if(rec.getInferredInsertSize()!=0)
					{
					label(margin1,"Insert-Size");pw.println(this.fmt.format(rec.getInferredInsertSize()));
					}
				label(margin1,"Mate-Contig");pw.println(rec.getMateReferenceName()+"  (index:"+rec.getMateReferenceIndex()+")");
				label(margin1,"Mate-Start");pw.println(this.fmt.format(rec.getMateAlignmentStart()));
				label(margin1,"Mate-Strand");pw.println(rec.getMateNegativeStrandFlag()?"-":"+");
				}
			
			final SAMReadGroupRecord grouprec = rec.getReadGroup();
			if(grouprec!=null )
				{
				label(margin1,"Read Group");
				pw.println();
				label(margin2,"ID");
				pw.println(grouprec.getId());
				for(Map.Entry<String,String> entry:grouprec.getAttributes())
					{
					label(margin2,entry.getKey());
					pw.println(entry.getValue());
					}
				}
			
			label(margin1,"Read-Length");
			pw.println(this.fmt.format(rec.getReadLength()));
			
			final Cigar cigar=rec.getCigar();
			if(cigar!=null && !cigar.isEmpty())
				{
				label(margin1,"Cigar");
				pw.println(rec.getCigarString()+" (N="+cigar.numCigarElements()+")");
				}
			
			final List<Base> align = new ArrayList<>();
			final String bases = rec.getReadString();
			final String quals = rec.getBaseQualityString();

			if(rec.getReadUnmappedFlag() || cigar==null || cigar.isEmpty())
				{
				for(int i=0;i< bases.length();i++)
					{
					final Base b=new Base();
					b.readpos=i;
					b.refbase = ' ';
					b.readpos = -1;
					b.cigaroperator = CigarOperator.P;
					b.readbase = (bases!=null && i>=0 && i<bases.length()?bases.charAt(i):'*');
					b.readqual = (quals!=null && i>=0 && i<quals.length()?quals.charAt(i):'?');
					align.add(b);
					}
				}
			else
				{
				int refpos=rec.getUnclippedStart();
				int readpos=0;
				if(cigar.numCigarElements()>1 && cigar.getCigarElement(0).getOperator()==CigarOperator.H)
					{
					readpos = rec.getUnclippedStart()-rec.getAlignmentStart();//WILL be negative !
					}
				
				
				final Function<Integer, Character> pos2base= (i)->(bases!=null && i>=0 && i<bases.length()?bases.charAt(i):'*');
				final Function<Integer, Character> pos2qual= (i)->(quals!=null && i>=0 && i<quals.length()?quals.charAt(i):'*');
				final Function<Integer, Character> pos2ref= (i)->getReferenceAt(rec.getContig(),i);
				
				
				for(final CigarElement ce:cigar)
					{
					final CigarOperator op = ce.getOperator();
					switch(op)
						{
						case P: break;
						case D: case N:
							{
							final Base b=new Base();
							b.cigaroperator = op;
							b.refpos  = refpos;
							b.refbase = pos2ref.apply(refpos);
							b.readbase = '^';
							b.readqual = '^';
							b.readpos = -1;
							align.add(b);
							refpos+=ce.getLength();
							break;
							}
						case X:case EQ:case M:case S:case H:case I:
							{
							for(int i=0;i< ce.getLength();++i)
								{
								final Base b=new Base();
								b.refpos=refpos;
								b.cigaroperator = op;
								b.readbase = pos2base.apply(readpos);
								b.readqual = pos2qual.apply(readpos);
								b.readpos=readpos;
								
								readpos++;//ok with 'H', because negative from beginning *
								if(op.equals(CigarOperator.I))
									{
									b.refpos = -1;
									b.refbase  = '^';
									}
								else
									{
									b.refpos= refpos;
									b.refbase  = pos2ref.apply(refpos);
									refpos++;
									}
								align.add(b);
								}
							break;
							}
						}
					}
				}
			
			if(!align.isEmpty())
				{
				label(margin1,"Sequence");
				pw.println();
				int x=0;
				final int FASTA_LEN=60;
				while(x<align.size())
					{
					for(int side=0;side<6;++side)
						{
						switch(side)
							{
							case 0: label(margin2,"Read ("+ this.fmt.format(align.stream().
									skip(x).
									mapToInt(B->B.readpos).
									filter(X -> X>=0).
									findFirst().orElse(-1))+")");
									break;
							case 4: label(margin2,"Qual");break;
							case 2:
								if(cigar==null || cigar.isEmpty()|| this.indexedFastaSequenceFile==null) continue;
								label(margin2,"Ref ("+this.fmt.format(align.stream().
										skip(x).
										mapToInt(B->B.refpos).
										filter(X -> X>0).
										findFirst().orElse(-1))+")");
								break;
							case 3:
								if(cigar==null || cigar.isEmpty()) continue;
								label(margin2,"Op");break;
							case 1:
								if(cigar==null || cigar.isEmpty() || this.indexedFastaSequenceFile==null) continue;
								label(margin2,"Mid");break;
							case 5: label(margin2,"Ref-Pos");break;
							default:break;
							}
						for(int y=0;y<FASTA_LEN && x+y<align.size();++y)
							{
							final Base base = align.get(x+y);
							if(y!=0 && y%10==0) pw.print(" ");
							final char c;
							switch(side)
								{
								case 0: c =  base.readbase;break;
								case 4: c =  base.readqual;break;
								case 2: c =  base.refbase;break;
								case 3: c = base.cigaroperator.name().charAt(0);break;
								case 1:
									c= base.cigaroperator.isAlignment()?
											(base.refbase==base.readbase?'|':' ')
											: ' ';
									break;
								case 5: {
									if(y%10==0 && base.refpos>0)
										{
										String s=String.valueOf(base.refpos);
										while(s.length()<10) s+=" ";
										pw.print(s);
										y+=(s.length()-1);/* -1 pour boucle for */
										}
									else
										{
										pw.print(" ");
										}
									continue;
									}
								default : c='?'; break;
								}
							pw.print(c);
							}
						pw.println();
						
						}
					x+=FASTA_LEN;
					pw.println();
					}
				}
			if(!rec.getAttributes().isEmpty()) {
				label(margin1,"Tags");
				pw.println();
				
				for(final SAMRecord.SAMTagAndValue tav: rec.getAttributes())
					{
					if(tav.tag.equals("SA")) continue;
					if(tav.tag.equals("RG")) continue;
					label(margin2,tav.tag);
					pw.print(" ");
					if(tav.value!=null && tav.value.getClass().isArray())
						{
						pw.print(" array(todo)");
						}
					else
						{
						pw.print(tav.value);
						}
					pw.print("   \"");
					pw.print(getTagDescription(tav.tag));
					pw.println("\"");
					}
			}
			
		if(rec.hasAttribute("SA"))
			{
			label(margin1,"Suppl. Alignments");
			pw.println();

			final List<List<String>> salist = new ArrayList<>();
	        final String semiColonStrs[] = SEMICOLON_PAT.split(rec.getStringAttribute("SA"));
	        for (int i = 0; i < semiColonStrs.length; ++i) {
	            final String semiColonStr = semiColonStrs[i];
	            if (semiColonStr.isEmpty()) continue;
	            final String commaStrs[] = COMMA_PAT.split(semiColonStr);
	            if (commaStrs.length != 6) continue;
	            salist.add(Arrays.asList(commaStrs));
	        	}
	        final String labels[]=new String[]{"CONTIG","POS","STRAND","CIGAR","QUAL","NM"};
	        final int collen[] = new int[labels.length];
	        for(int i=0;i<labels.length;++i)
	        	{
	        	final int colidx=i;
	        	collen[i] = Math.max(
	        			salist.stream().mapToInt(L->L.get(colidx).length()).max().getAsInt(),
	        			labels[i].length());
	        	}
	        int y=-1;
	        while(y<salist.size())
		        {
	        	for(int x=0;x<margin2;++x) pw.print(" ");
				for(int i=0;i<labels.length;i++)
					{
					String s = (y==-1?labels[i]:salist.get(y).get(i));
					while(s.length()<collen[i]) s+=" ";
					if(i>0) pw.print(" | ");
					pw.print(s);
					}
				++y;
				pw.println();
		        }
	        
			}	
			
			
			pw.println("<<<<< "+nLine);
			pw.flush();
			}
		
		@Override
		public void close() {
			pw.close();
			CloserUtil.close(this.indexedFastaSequenceFile);
			this.genomicSequence=null;
			}
		}
	@Override
	public int doWork(final List<String> args) {
		SamReader r = null;
		PrettySAMWriter out = null;
		CloseableIterator<SAMRecord> iter = null;
		try 
			{
			r= super.openSamReader(oneFileOrNull(args));
			out = new PrettySAMWriter(super.openFileOrStdoutAsPrintWriter(this.outputFile));
			out.writeHeader(r.getFileHeader());
			out.setReferenceFile(this.refFile);
			iter = r.iterator();
			while(iter.hasNext())
				{
				out.addAlignment(iter.next());
				}
			out.close();out=null;
			return 0;
			}
		catch(final Throwable err)
			{
			LOG.error(err);
			return -1;
			}
		finally
			{
			CloserUtil.close(iter);
			CloserUtil.close(r);
			CloserUtil.close(out);
			}
		}
	
	public static void main(final String[] args) {
		new PrettySam().instanceMainWithExit(args);
	}

}
