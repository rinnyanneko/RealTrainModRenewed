package jp.kaiz.atsassistmod.util;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Comparison operators for IFTTT conditions (faithful port; Vec uses MC Vec3). */
public class ComparisonManager {
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    public interface ComparisonBase<T> {
        java.lang.String getName();
        boolean isTrue(T o0, Object o1);
        T parseT(java.lang.String str);
    }

    public enum Integer implements ComparisonBase<java.lang.Integer> {
        EQUAL("==") { public boolean isTrue(java.lang.Integer a, Object b) { return a.equals(b); } },
        GREATER_THAN(">") { public boolean isTrue(java.lang.Integer a, Object b) { return a > (java.lang.Integer) b; } },
        GREATER_EQUAL(">=") { public boolean isTrue(java.lang.Integer a, Object b) { return a >= (java.lang.Integer) b; } },
        LESS_THAN("<") { public boolean isTrue(java.lang.Integer a, Object b) { return a < (java.lang.Integer) b; } },
        LESS_EQUAL("<=") { public boolean isTrue(java.lang.Integer a, Object b) { return a <= (java.lang.Integer) b; } },
        NOT_EQUAL("!=") { public boolean isTrue(java.lang.Integer a, Object b) { return !a.equals(b); } };
        private final java.lang.String name;
        Integer(java.lang.String name) { this.name = name; }
        public java.lang.String getName() { return name; }
        public abstract boolean isTrue(java.lang.Integer a, Object b);
        public java.lang.Integer parseT(java.lang.String s) { try { return java.lang.Integer.parseInt(s); } catch (Exception e) { return 0; } }
    }

    public enum Double implements ComparisonBase<java.lang.Double> {
        EQUAL("==") { public boolean isTrue(java.lang.Double a, Object b) { return a.equals(b); } },
        GREATER_THAN(">") { public boolean isTrue(java.lang.Double a, Object b) { return a > (java.lang.Double) b; } },
        GREATER_EQUAL(">=") { public boolean isTrue(java.lang.Double a, Object b) { return a >= (java.lang.Double) b; } },
        LESS_THAN("<") { public boolean isTrue(java.lang.Double a, Object b) { return a < (java.lang.Double) b; } },
        LESS_EQUAL("<=") { public boolean isTrue(java.lang.Double a, Object b) { return a <= (java.lang.Double) b; } },
        NOT_EQUAL("!=") { public boolean isTrue(java.lang.Double a, Object b) { return !a.equals(b); } };
        private final java.lang.String name;
        Double(java.lang.String name) { this.name = name; }
        public java.lang.String getName() { return name; }
        public abstract boolean isTrue(java.lang.Double a, Object b);
        public java.lang.Double parseT(java.lang.String s) { try { return java.lang.Double.parseDouble(s); } catch (Exception e) { return 0d; } }
    }

    public enum String implements ComparisonBase<java.lang.String> {
        EQUAL("==") { public boolean isTrue(java.lang.String a, Object b) { return a.equals(b); } },
        NOT_EQUAL("!=") { public boolean isTrue(java.lang.String a, Object b) { return !a.equals(b); } },
        CONTAINS(" contains ") { public boolean isTrue(java.lang.String a, Object b) { return a.contains((java.lang.String) b); } },
        NOT_CONTAINS(" !contains ") { public boolean isTrue(java.lang.String a, Object b) { return !a.contains((java.lang.String) b); } };
        private final java.lang.String name;
        String(java.lang.String name) { this.name = name; }
        public java.lang.String getName() { return name; }
        public abstract boolean isTrue(java.lang.String a, Object b);
        public java.lang.String parseT(java.lang.String s) { return s; }
    }

    public enum Boolean implements ComparisonBase<java.lang.Boolean> {
        TRUE("==True") { public boolean isTrue(java.lang.Boolean a, Object b) { return a; } },
        FALSE("==False") { public boolean isTrue(java.lang.Boolean a, Object b) { return !a; } };
        private final java.lang.String name;
        Boolean(java.lang.String name) { this.name = name; }
        public java.lang.String getName() { return name; }
        public abstract boolean isTrue(java.lang.Boolean a, Object b);
        public java.lang.Boolean parseT(java.lang.String s) { try { return java.lang.Boolean.parseBoolean(s); } catch (Exception e) { return false; } }
    }
}
