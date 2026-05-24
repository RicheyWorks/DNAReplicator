package com.xai.dnareplicator.service;

import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.controller.DNAProcessingException;
import com.xai.dnareplicator.model.DNAFragment;
import com.xai.dnareplicator.model.Protein;
import com.xai.dnareplicator.model.SimulationSession;
import com.xai.dnareplicator.view.SimulationView;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DNAService {
    private final SimulationSession session;
    private final List<DNAFragment> spliceHistory;
    private final SimulationView view;
    private double mutationRate;
    private final HashMap<String, DNAFragment> fragmentCache; // CLRS Chapter 11: Hash Tables for fragment lookup
    private final HashMap<String, String> lcsCache; // CLRS Chapter 11: Cache for LCS results

    public DNAService(SimulationSession session, SimulationView view) {
        if (session == null) {
            throw new IllegalArgumentException("SimulationSession cannot be null");
        }
        if (view == null) {
            throw new IllegalArgumentException("SimulationView cannot be null");
        }
        this.session = session;
        this.spliceHistory = new ArrayList<>();
        this.view = view;
        this.mutationRate = 0.2;
        this.fragmentCache = new HashMap<>();
        this.lcsCache = new HashMap<>();
    }

    public List<DNAFragment> getDNAFragments() {
        return session.getDnaFragments();
    }

    public List<Protein> getProteins() {
        return session.getProteins();
    }

    public void setMutationRate(double mutationRate) {
        if (mutationRate < 0 || mutationRate > 1) {
            view.updateStatus("Invalid mutation rate: must be between 0 and 1");
            return;
        }
        this.mutationRate = mutationRate;
    }

    // CLRS Chapter 11: Cache fragment for O(1) lookup
    public void cacheFragment(DNAFragment fragment) throws DNAProcessingException {
        if (fragment == null || fragment.getId() == null || fragment.getId().isEmpty()) {
            throw new DNAProcessingException("Cannot cache fragment: null or invalid ID");
        }
        fragmentCache.put(fragment.getId(), fragment);
    }

    // CLRS Chapter 11: Retrieve cached fragment
    public DNAFragment getFragment(String id) throws DNAProcessingException {
        if (id == null || id.isEmpty()) {
            throw new DNAProcessingException("Cannot retrieve fragment: null or empty ID");
        }
        DNAFragment fragment = fragmentCache.get(id);
        if (fragment == null) {
            throw new DNAProcessingException("Fragment with ID " + id + " not found in cache");
        }
        return fragment;
    }

    public void spawnDNAFragments(int fragmentsRequired) {
        if (fragmentsRequired <= 0) {
            view.updateStatus("Invalid number of fragments: must be positive");
            return;
        }
        session.getDnaFragments().clear();
        fragmentCache.clear();
        lcsCache.clear();
        for (int i = 0; i < fragmentsRequired; i++) {
            double x = 200 + Config.RAND.nextDouble() * 400;
            double y = 100 + Config.RAND.nextDouble() * 300;
            try {
                DNAFragment fragment = new DNAFragment(x, y, "Fragment " + (i + 1));
                if (fragment.getBasePairs() == null || fragment.getBasePairs().isEmpty()) {
                    view.updateStatus("Fragment " + fragment.getName() + " has empty sequence");
                    continue;
                }
                if (fragment.getBasePairs().length() > Config.MAX_DNA_FRAGMENT_LENGTH) {
                    view.updateStatus("Fragment " + fragment.getName() + " exceeds max length!");
                    continue;
                }
                session.getDnaFragments().add(fragment);
                cacheFragment(fragment);
                view.addDNAFragment(fragment, x, y, fragment.getBasePairs(), false,
                    () -> toggleSelection(fragment), () -> updateFragmentPosition(fragment));
            } catch (DNAProcessingException e) {
                view.updateStatus("Failed to spawn fragment: " + e.getMessage());
            }
        }
    }

    public void toggleSelection(DNAFragment fragment) {
        if (fragment == null) {
            view.updateStatus("Cannot toggle selection: null fragment");
            return;
        }
        fragment.setSelected(!fragment.isSelected());
        view.updateDNAFragment(fragment, fragment.getX(), fragment.getY(), fragment.getBasePairs(), fragment.isSelected());
        int selectedCount = (int) session.getDnaFragments().stream().filter(DNAFragment::isSelected).count();
        view.updateStatus("Selected " + selectedCount + " DNA fragments");
    }

    public void updateFragmentPosition(DNAFragment fragment) {
        if (fragment == null) {
            view.updateStatus("Cannot update position: null fragment");
            return;
        }
        fragment.setX(fragment.getX());
        fragment.setY(fragment.getY());
        view.updateDNAFragment(fragment, fragment.getX(), fragment.getY(), fragment.getBasePairs(), fragment.isSelected());
    }

    // CLRS Chapter 15: Dynamic Programming (Longest Common Subsequence) with Memoization
    public String alignFragments(DNAFragment f1, DNAFragment f2) throws DNAProcessingException {
        if (f1 == null || f2 == null) {
            throw new DNAProcessingException("Cannot align fragments: null fragment provided");
        }
        if (f1.getId() == null || f2.getId() == null || f1.getId().isEmpty() || f2.getId().isEmpty()) {
            throw new DNAProcessingException("Cannot align fragments: invalid fragment ID");
        }
        String s1 = f1.getBasePairs();
        String s2 = f2.getBasePairs();
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            throw new DNAProcessingException("Cannot align fragments: empty or null sequence");
        }
        if (s1.length() > Config.MAX_DNA_FRAGMENT_LENGTH || s2.length() > Config.MAX_DNA_FRAGMENT_LENGTH) {
            throw new DNAProcessingException("Fragments exceed max length for alignment: " + s1.length() + ", " + s2.length());
        }

        // Generate cache key
        String cacheKey = f1.getId().compareTo(f2.getId()) < 0
            ? f1.getId() + ":" + f2.getId()
            : f2.getId() + ":" + f1.getId();

        // Check LCS cache (CLRS Chapter 11)
        if (lcsCache.containsKey(cacheKey)) {
            view.updateStatus("Retrieved cached alignment for " + f1.getName() + " and " + f2.getName());
            return lcsCache.get(cacheKey);
        }

        // Compute LCS using dynamic programming
        int m = s1.length(), n = s2.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        StringBuilder aligned = new StringBuilder();
        int i = m, j = n;
        while (i > 0 && j > 0) {
            if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                aligned.append(s1.charAt(i - 1));
                i--; j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }

        String result = aligned.reverse().toString();
        double alignmentScore = (double) result.length() / Math.max(s1.length(), s2.length());
        if (alignmentScore < Config.DNA_ALIGNMENT_SCORE_THRESHOLD) {
            throw new DNAProcessingException("Alignment score " + String.format("%.2f", alignmentScore) + " below threshold");
        }

        // Cache the result
        lcsCache.put(cacheKey, result);
        view.updateStatus("Cached new alignment for " + f1.getName() + " and " + f2.getName());
        return result;
    }

    public void spliceDNA() {
        List<DNAFragment> selectedFragments = session.getDnaFragments().stream()
            .filter(DNAFragment::isSelected)
            .collect(Collectors.toList());

        if (selectedFragments.size() < 2) {
            view.updateStatus("Select at least 2 DNA fragments to splice!");
            return;
        }

        DNAFragment fragment1 = selectedFragments.get(0);
        DNAFragment fragment2 = selectedFragments.get(1);
        if (fragment1 == null || fragment2 == null) {
            view.updateStatus("Invalid selected fragments!");
            return;
        }
        if (Math.abs(fragment1.getX() - fragment2.getX()) > 50 || Math.abs(fragment1.getY() - fragment2.getY()) > 50) {
            view.updateStatus("Move selected fragments closer to splice!");
            return;
        }

        // CLRS Chapter 15: Align fragments
        String alignedSequence;
        try {
            alignedSequence = alignFragments(fragment1, fragment2);
        } catch (DNAProcessingException e) {
            view.updateStatus("Splicing failed: " + e.getMessage());
            return;
        }

        // CLRS Chapter 5: Randomized mutation
        if (Config.RAND.nextDouble() > (1 - mutationRate)) {
            view.updateStatus("Splicing failed due to mutation error!");
            try {
                fragment1.mutate();
                fragment2.mutate();
                invalidateLcsCache(fragment1);
                invalidateLcsCache(fragment2);
                view.showMutationFailure(fragment1);
                view.showMutationFailure(fragment2);
                view.updateDNAFragment(fragment1, fragment1.getX(), fragment1.getY(), fragment1.getBasePairs(), fragment1.isSelected());
                view.updateDNAFragment(fragment2, fragment2.getX(), fragment2.getY(), fragment2.getBasePairs(), fragment2.isSelected());
            } catch (Exception e) {
                view.updateStatus("Mutation error: " + e.getMessage());
            }
            return;
        }

        view.updateStatus("Splicing " + fragment1.getName() + " and " + fragment2.getName() + "...");
        session.getDnaFragments().remove(fragment1);
        session.getDnaFragments().remove(fragment2);
        fragmentCache.remove(fragment1.getId());
        fragmentCache.remove(fragment2.getId());
        invalidateLcsCache(fragment1);
        invalidateLcsCache(fragment2);
        view.removeDNAFragment(fragment1);
        view.removeDNAFragment(fragment2);
        spliceHistory.add(fragment1);
        spliceHistory.add(fragment2);

        DNAFragment spliced = new DNAFragment(fragment1.getX(), fragment1.getY(), "Spliced DNA");
        spliced.setBasePairs(alignedSequence);
        session.getDnaFragments().add(spliced);
        try {
            cacheFragment(spliced);
        } catch (DNAProcessingException e) {
            view.updateStatus("Failed to cache spliced fragment: " + e.getMessage());
            return;
        }
        view.addDNAFragment(spliced, spliced.getX(), spliced.getY(), spliced.getBasePairs(), false,
            () -> toggleSelection(spliced), () -> updateFragmentPosition(spliced));

        int proteinCount = Config.RAND.nextInt(5) + 1;
        for (int i = 0; i < proteinCount; i++) {
            String enzymeType = view.promptForEnzymeType();
            if (enzymeType == null || enzymeType.isEmpty()) {
                view.updateStatus("Invalid enzyme type for protein");
                continue;
            }
            Protein protein = new Protein(fragment1.getX() + i * 20, fragment1.getY() + 20, enzymeType);
            session.getProteins().add(protein);
            view.addProtein(protein, protein.getX(), protein.getY(), false, false, protein.getEnzymeType(), protein.getViralResistance());
        }
    }

    private void invalidateLcsCache(DNAFragment fragment) {
        if (fragment == null || fragment.getId() == null) {
            return;
        }
        String id = fragment.getId();
        lcsCache.keySet().removeIf(key -> key.startsWith(id + ":") || key.endsWith(":" + id));
    }

    // CLRS Chapter 4: Divide-and-Conquer (Merge Sort)
    public List<DNAFragment> sortFragmentsByLength() {
        return sortFragmentsByLength(new ArrayList<>(session.getDnaFragments()));
    }

    private List<DNAFragment> sortFragmentsByLength(List<DNAFragment> fragments) {
        if (fragments.size() <= 1) {
            return new ArrayList<>(fragments);
        }
        int mid = fragments.size() / 2;
        List<DNAFragment> left = sortFragmentsByLength(new ArrayList<>(fragments.subList(0, mid)));
        List<DNAFragment> right = sortFragmentsByLength(new ArrayList<>(fragments.subList(mid, fragments.size())));
        return merge(left, right);
    }

    private List<DNAFragment> merge(List<DNAFragment> left, List<DNAFragment> right) {
        List<DNAFragment> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < left.size() && j < right.size()) {
            String leftSeq = left.get(i).getBasePairs();
            String rightSeq = right.get(j).getBasePairs();
            int leftLen = (leftSeq != null) ? leftSeq.length() : 0;
            int rightLen = (rightSeq != null) ? rightSeq.length() : 0;
            if (leftLen <= rightLen) {
                result.add(left.get(i++));
            } else {
                result.add(right.get(j++));
            }
        }
        result.addAll(left.subList(i, left.size()));
        result.addAll(right.subList(j, right.size()));
        return result;
    }

    public void undoSplice() {
        if (spliceHistory.size() < 2) {
            view.updateStatus("No splicing to undo!");
            return;
        }

        DNAFragment fragment2 = spliceHistory.remove(spliceHistory.size() - 1);
        DNAFragment fragment1 = spliceHistory.remove(spliceHistory.size() - 1);
        if (fragment1 == null || fragment2 == null) {
            view.updateStatus("Invalid fragments in splice history!");
            return;
        }
        session.getDnaFragments().add(fragment1);
        session.getDnaFragments().add(fragment2);
        try {
            cacheFragment(fragment1);
            cacheFragment(fragment2);
        } catch (DNAProcessingException e) {
            view.updateStatus("Failed to re-cache fragments: " + e.getMessage());
            return;
        }
        view.addDNAFragment(fragment1, fragment1.getX(), fragment1.getY(), fragment1.getBasePairs(), false,
            () -> toggleSelection(fragment1), () -> updateFragmentPosition(fragment1));
        view.addDNAFragment(fragment2, fragment2.getX(), fragment2.getY(), fragment2.getBasePairs(), false,
            () -> toggleSelection(fragment2), () -> updateFragmentPosition(fragment2));

        if (!session.getDnaFragments().isEmpty()) {
            DNAFragment spliced = session.getDnaFragments().get(session.getDnaFragments().size() - 1);
            session.getDnaFragments().remove(spliced);
            fragmentCache.remove(spliced.getId());
            invalidateLcsCache(spliced);
            view.removeDNAFragment(spliced);
        }
        session.getProteins().clear();
        view.clearAll();
        for (DNAFragment fragment : session.getDnaFragments()) {
            if (fragment != null) {
                view.addDNAFragment(fragment, fragment.getX(), fragment.getY(), fragment.getBasePairs(), fragment.isSelected(),
                    () -> toggleSelection(fragment), () -> updateFragmentPosition(fragment));
            }
        }
        view.updateStatus("Splicing undone!");
    }

    public void exportDNA() {
        if (session.getDnaFragments().isEmpty()) {
            view.updateStatus("No DNA fragments to export!");
            return;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(Config.FASTA_EXPORT_PATH))) {
            List<DNAFragment> sortedFragments = sortFragmentsByLength();
            for (DNAFragment fragment : sortedFragments) {
                if (fragment == null || fragment.getBasePairs() == null || fragment.getName() == null) {
                    view.updateStatus("Skipping invalid fragment during export");
                    continue;
                }
                writer.println(">" + fragment.getName());
                writer.println(fragment.getBasePairs());
            }
            view.updateStatus("DNA exported to " + Config.FASTA_EXPORT_PATH + "!");
        } catch (IOException e) {
            view.updateStatus("Failed to export DNA: " + e.getMessage());
        }
    }

    public void importDNA() {
        try (BufferedReader reader = new BufferedReader(new FileReader(Config.FASTA_EXPORT_PATH))) {
            session.getDnaFragments().clear();
            fragmentCache.clear();
            lcsCache.clear();
            view.clearAll();
            String line;
            String name = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(">")) {
                    name = line.substring(1);
                    if (name == null || name.isEmpty()) {
                        view.updateStatus("Invalid fragment name in FASTA file");
                        continue;
                    }
                } else if (name != null) {
                    if (line == null || line.isEmpty()) {
                        view.updateStatus("Empty sequence for fragment " + name);
                        name = null;
                        continue;
                    }
                    if (line.length() > Config.MAX_DNA_FRAGMENT_LENGTH) {
                        view.updateStatus("Imported fragment exceeds max length!");
                        name = null;
                        continue;
                    }
                    double x = 200 + Config.RAND.nextDouble() * 400;
                    double y = 100 + Config.RAND.nextDouble() * 300;
                    try {
                        DNAFragment fragment = new DNAFragment(x, y, name);
                        fragment.setBasePairs(line);
                        session.getDnaFragments().add(fragment);
                        cacheFragment(fragment);
                        view.addDNAFragment(fragment, x, y, line, false,
                            () -> toggleSelection(fragment), () -> updateFragmentPosition(fragment));
                    } catch (DNAProcessingException e) {
                        view.updateStatus("Failed to import fragment: " + e.getMessage());
                    }
                    name = null;
                }
            }
            view.updateStatus("DNA imported from " + Config.FASTA_EXPORT_PATH + "!");
        } catch (IOException e) {
            view.updateStatus("Failed to import DNA: " + e.getMessage());
        }
    }

    public void clear() {
        session.clearDnaAndProteins();
        spliceHistory.clear();
        fragmentCache.clear();
        lcsCache.clear();
        view.clearAll();
    }
}
