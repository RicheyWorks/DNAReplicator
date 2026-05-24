package com.xai.dnareplicator.algorithm.dna;

import com.xai.dnareplicator.model.DNAFragment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MergeSortByLengthTest {

    @Test
    void sort_ordersBySequenceLength() {
        DNAFragment shortFrag = new DNAFragment(0, 0, "short");
        shortFrag.setBasePairs("AG");

        DNAFragment longFrag = new DNAFragment(0, 0, "long");
        longFrag.setBasePairs("AGTCAGTC");

        DNAFragment midFrag = new DNAFragment(0, 0, "mid");
        midFrag.setBasePairs("AGTC");

        List<DNAFragment> sorted = MergeSortByLength.sort(List.of(longFrag, shortFrag, midFrag));

        assertEquals("short", sorted.get(0).getName());
        assertEquals("mid", sorted.get(1).getName());
        assertEquals("long", sorted.get(2).getName());
    }
}
