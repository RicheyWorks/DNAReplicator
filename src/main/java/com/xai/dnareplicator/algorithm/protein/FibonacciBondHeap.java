package com.xai.dnareplicator.algorithm.protein;

import com.xai.dnareplicator.domain.protein.ProteinBond;

import java.util.ArrayList;
import java.util.List;

/** Fibonacci heap for fast bond retrieval (CLRS Chapter 19). */
public class FibonacciBondHeap {

    private static class Node {
        ProteinBond bond;
        double key;
        Node parent;
        Node child;
        Node left;
        Node right;
        int degree;
        boolean mark;

        Node(ProteinBond bond, double key) {
            this.bond = bond;
            this.key = key;
            this.left = this;
            this.right = this;
        }
    }

    private Node min;
    private int size;

    public void insert(ProteinBond bond, double key) {
        Node node = new Node(bond, key);
        if (min == null) {
            min = node;
        } else {
            mergeLists(min, node);
            if (node.key < min.key) {
                min = node;
            }
        }
        size++;
    }

    public ProteinBond extractMin() {
        if (min == null) {
            return null;
        }
        Node z = min;
        ProteinBond result = z.bond;
        if (z.child != null) {
            Node x = z.child;
            do {
                Node next = x.right;
                mergeLists(min, x);
                x.parent = null;
                x = next;
            } while (x != z.child);
        }
        if (z == z.right) {
            min = null;
        } else {
            min = z.right;
            z.left.right = z.right;
            z.right.left = z.left;
            consolidate();
        }
        size--;
        return result;
    }

    private void mergeLists(Node a, Node b) {
        Node aRight = a.right;
        Node bLeft = b.left;
        a.right = b;
        b.left = a;
        bLeft.right = aRight;
        aRight.left = bLeft;
    }

    private void consolidate() {
        int maxDegree = (int) Math.floor(Math.log(size) / Math.log(2)) + 1;
        Node[] buckets = new Node[maxDegree + 1];
        List<Node> roots = new ArrayList<>();
        Node x = min;
        if (x != null) {
            do {
                roots.add(x);
                x = x.right;
            } while (x != min);
        }
        for (Node w : roots) {
            x = w;
            int d = x.degree;
            while (buckets[d] != null) {
                Node y = buckets[d];
                if (x.key > y.key) {
                    Node temp = x;
                    x = y;
                    y = temp;
                }
                link(y, x);
                buckets[d] = null;
                d++;
            }
            buckets[d] = x;
        }
        min = null;
        for (Node node : buckets) {
            if (node != null) {
                if (min == null) {
                    min = node;
                    node.left = node;
                    node.right = node;
                } else {
                    mergeLists(min, node);
                    if (node.key < min.key) {
                        min = node;
                    }
                }
            }
        }
    }

    private void link(Node y, Node x) {
        y.left.right = y.right;
        y.right.left = y.left;
        y.parent = x;
        if (x.child == null) {
            x.child = y;
            y.right = y;
            y.left = y;
        } else {
            mergeLists(x.child, y);
        }
        x.degree++;
        y.mark = false;
    }

    public boolean isEmpty() {
        return min == null;
    }
}
