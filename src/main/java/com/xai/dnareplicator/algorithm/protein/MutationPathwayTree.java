package com.xai.dnareplicator.algorithm.protein;

import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.domain.protein.FoldingPathway;
import com.xai.dnareplicator.domain.protein.ProteinBond;

import java.util.ArrayList;
import java.util.List;

/** Mutation tree recording folding pathway evolution (educational). */
public class MutationPathwayTree {

    private static class Node {
        FoldingPathway pathway;
        List<Node> children;
        String eventType;

        Node(FoldingPathway pathway, String eventType) {
            this.pathway = pathway;
            this.children = new ArrayList<>();
            this.eventType = eventType;
        }
    }

    private Node root;

    public void addPathway(FoldingPathway pathway, FoldingPathway parent, String eventType) {
        Node newNode = new Node(pathway, eventType);
        if (root == null && parent == null) {
            root = newNode;
        } else if (parent != null) {
            Node parentNode = findNode(root, parent);
            if (parentNode != null) {
                parentNode.children.add(newNode);
            }
        }
    }

    public void seedNewPathway(FoldingPathway successfulPathway) {
        if (successfulPathway.getCompositeScore() >= 0.5) {
            List<ProteinBond> seededBonds = new ArrayList<>(successfulPathway.getBonds());
            if (!seededBonds.isEmpty() && Config.RAND.nextDouble() < 0.2) {
                int index = Config.RAND.nextInt(seededBonds.size());
                ProteinBond existing = seededBonds.get(index);
                seededBonds.set(index, new ProteinBond(
                        existing.getAminoAcid1(),
                        existing.getAminoAcid2(),
                        Config.RAND.nextDouble() * Config.BOND_ENERGY_THRESHOLD,
                        Config.RAND.nextDouble() < 0.5 ? "L" : "R"));
            }
            FoldingPathway seededPathway = new FoldingPathway(seededBonds);
            addPathway(seededPathway, successfulPathway, "Seed");
        }
    }

    private Node findNode(Node node, FoldingPathway pathway) {
        if (node == null) {
            return null;
        }
        if (node.pathway == pathway) {
            return node;
        }
        for (Node child : node.children) {
            Node found = findNode(child, pathway);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public void visualize() {
        // Placeholder for future UI visualization hook
    }
}
