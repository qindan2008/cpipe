// vim: ts=4:expandtab:sw=4:cindent
/////////////////////////////////////////////////////////////////////////////////
//
// This file is part of Cpipe.
// 
// Cpipe is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, under version 3 of the License, subject
// to additional terms compatible with the GNU General Public License version 3,
// specified in the LICENSE file that is part of the Cpipe distribution.
//
// Cpipe is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of 
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Cpipe.  If not, see <http://www.gnu.org/licenses/>.
//
/////////////////////////////////////////////////////////////////////////////////
//
// Conversion script for preparing target region BED files for use in
// the pipeline. What it does:
//
//     1. sorts the bed files by chromosome and start region
//     2. merges overlapping regions together so that the BED file consists of 
//        non-overlapping regions
//     3. annotates to each of these regions the gene that is found in the RefSeq
//        database that is to be used by the pipeline (this ensures 
//        consistency, in case the incoming BED files use different variant of
//        the gene name to RefSeq)
//     4. names the BED file to a standard form, <SYMBOL>.bed, where the
//        <SYMBOL> part is the name of the target region (cohort, flagship).
//        
// Note: you may need to adjust the  split pattern below (in the run {} section)
//       so that it correctly selects the SYMBOL for the target region from the
//       names of your input files.
//
//////////////////////////////////////////////////////////////////////////////


load "../config.groovy"
load "../pipeline_stages_config.groovy"

if(!file(BASE).exists()) {
  println "="*80
  println "Please supply the BASE parameter using -p BASE=... when running the pipeline to point at the pipeline distriubtion root."
  println "="*80
  System.exit(1)
}

flatten = {
    exec """
        echo "Flattening and removing nonstandard chromosomes ..."

        $BEDTOOLS/bin/sortBed -i $input.bed | $BEDTOOLS/bin/bedtools merge -i - | sed 's/;.*\$//' | grep -v '^chr[0-9]_' > $output.bed
    """
}

annotate = {
    exec """
        echo "Annotating genes ..."

        JAVA_OPTS="-Xmx1g" $GROOVY -cp $GROOVY_NGS/groovy-ngs-utils.jar $BASE/pipeline/scripts/annotate_genes.groovy -r $ANNOVAR_DB/hg19_refGene.txt $input.bed > $output.bed
    """
}

sort = {
    exec """
        echo "Sorting ..."

        $BASE/tools/IGVTools/2.3.6/igvtools.lowmem sort $input.bed $output.bed
    """
}

rename = {
    produce(TARGET_REGION_ID + '.bed') {
        exec "cp $input.bed $output.bed"
    }
}

extract_pgx = {
    msg "Processing PGX variants for target $branch"
    produce(branch.name + '.pgx.vcf') {
        exec """
            cat $DBSNP | JAVA_OPTS="-Xmx1g" $GROOVY 
                -cp $GROOVY_NGS/groovy-ngs-utils.jar
                -e 'pgx = new File("$input.txt").readLines()*.trim(); VCF.filter { it.id in pgx }' > $output.vcf
        """
    }
}


success = {
    produce("succeeded.txt") {
	    exec """
		 touch $output.txt
		 """
    }
}

requires TARGET_REGION_ID : "Identifier for target region (will become name of output bed file, do not include .bed suffix, eg: NEXTERA)"

run {
      // Examples of how to process multiple files
      //"RefSeq_coding_%.bed" * [ flatten + annotate + sort + rename ] +
      // ~"(.*)_Covered.bed" * [ flatten + annotate + sort + rename ]// +
      // "%.pgx.txt" * [ extract_pgx + annotate_vep ] 
      flatten + annotate + sort + rename + success
    }


