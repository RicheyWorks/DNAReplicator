package com.xai.dnareplicator.algorithm.dna;

/**
 * Longest Common Subsequence via dynamic programming (CLRS Chapter 15).
 */
public final class LongestCommonSubsequence {

    private LongestCommonSubsequence() {
    }

    public static String align(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return "";
        }
        int m = s1.length();
        int n = s2.length();
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
        int i = m;
        int j = n;
        while (i > 0 && j > 0) {
            if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                aligned.append(s1.charAt(i - 1));
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        return aligned.reverse().toString();
    }

    public static double alignmentScore(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }
        String lcs = align(s1, s2);
        return (double) lcs.length() / Math.max(s1.length(), s2.length());
    }
}
