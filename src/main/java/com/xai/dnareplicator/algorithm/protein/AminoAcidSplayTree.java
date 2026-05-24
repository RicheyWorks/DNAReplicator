package com.xai.dnareplicator.algorithm.protein;

import com.xai.dnareplicator.domain.protein.AminoAcid;

import java.util.ArrayList;
import java.util.List;

/** Splay tree for amino acid ordering (CLRS Chapter 17). */
public class AminoAcidSplayTree {

    private static class Node {
        AminoAcid acid;
        Node left;
        Node right;

        Node(AminoAcid acid) {
            this.acid = acid;
        }
    }

    private Node root;

    public void insert(AminoAcid acid) {
        root = splayInsert(root, acid);
    }

    private Node splayInsert(Node node, AminoAcid acid) {
        if (node == null) {
            return new Node(acid);
        }
        if (acid.getId().compareTo(node.acid.getId()) < 0) {
            node.left = splayInsert(node.left, acid);
        } else {
            node.right = splayInsert(node.right, acid);
        }
        return node;
    }

    public List<AminoAcid> getAminoAcids() {
        List<AminoAcid> acids = new ArrayList<>();
        inOrderTraversal(root, acids);
        return acids;
    }

    private void inOrderTraversal(Node node, List<AminoAcid> acids) {
        if (node != null) {
            inOrderTraversal(node.left, acids);
            acids.add(node.acid);
            inOrderTraversal(node.right, acids);
        }
    }
}
