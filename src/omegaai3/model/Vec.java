package omegaai3.model;

public record Vec(double x, double y) {
    public static final Vec ZERO = new Vec(0, 0);
    public Vec { if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Non-finite position"); }
    public Vec add(Vec b) { return new Vec(x + b.x, y + b.y); }
    public Vec sub(Vec b) { return new Vec(x - b.x, y - b.y); }
    public Vec scale(double n) { return new Vec(x * n, y * n); }
    public double dot(Vec b) { return x * b.x + y * b.y; }
    public double length() { return Math.hypot(x, y); }
    public double distance(Vec b) { return sub(b).length(); }
    public Vec unit() { double n = length(); return n < 1e-6 ? ZERO : scale(1 / n); }
    public double bearing() { return Math.toDegrees(Math.atan2(y, x)); }
    public Vec clamp(double halfWidth, double halfHeight, double margin) {
        double w = Math.max(0, halfWidth - margin), h = Math.max(0, halfHeight - margin);
        return new Vec(Math.max(-w, Math.min(w, x)), Math.max(-h, Math.min(h, y)));
    }
    public static double angleDifference(double a, double b) {
        double d = (a - b) % 360;
        return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
    }
}
