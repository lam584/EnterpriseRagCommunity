package com.example.EnterpriseRagCommunity.service;

public final class NumericClampSupport {

    private NumericClampSupport() {
    }

    public static double clampDouble(Double v, double min, double max, double def) {
        double x = v == null ? def : v;
        if (Double.isNaN(x) || Double.isInfinite(x)) x = def;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }
}
