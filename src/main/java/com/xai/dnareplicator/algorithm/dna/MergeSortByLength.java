package com.xai.dnareplicator.algorithm.dna;

import com.xai.dnareplicator.model.DNAFragment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Merge sort DNA fragments by base-pair sequence length (CLRS Chapter 4).
 */
public final class MergeSortByLength {

    private static final Comparator<DNAFragment> BY_LENGTH = Comparator.comparingInt(
            f -> {
                String seq = f.getBasePairs();
                return (seq != null) ? seq.length() : 0;
            });

    private MergeSortByLength() {
    }

    public static List<DNAFragment> sort(List<DNAFragment> fragments) {
        if (fragments == null || fragments.size() <= 1) {
            return new ArrayList<>(fragments != null ? fragments : List.of());
        }
        return mergeSort(new ArrayList<>(fragments));
    }

    private static List<DNAFragment> mergeSort(List<DNAFragment> fragments) {
        if (fragments.size() <= 1) {
            return fragments;
        }
        int mid = fragments.size() / 2;
        List<DNAFragment> left = mergeSort(new ArrayList<>(fragments.subList(0, mid)));
        List<DNAFragment> right = mergeSort(new ArrayList<>(fragments.subList(mid, fragments.size())));
        return merge(left, right);
    }

    private static List<DNAFragment> merge(List<DNAFragment> left, List<DNAFragment> right) {
        List<DNAFragment> result = new ArrayList<>(left.size() + right.size());
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            if (BY_LENGTH.compare(left.get(i), right.get(j)) <= 0) {
                result.add(left.get(i++));
            } else {
                result.add(right.get(j++));
            }
        }
        while (i < left.size()) {
            result.add(left.get(i++));
        }
        while (j < right.size()) {
            result.add(right.get(j++));
        }
        return result;
    }
}
