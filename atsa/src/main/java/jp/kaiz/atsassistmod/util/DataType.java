package jp.kaiz.atsassistmod.util;

/** Local copy of RTM's modelpack DataType (used by IFTTT data-map rules). */
public enum DataType {
    BOOLEAN("bool"),
    INT("int"),
    HEX("hex"),
    DOUBLE("double"),
    STRING("string"),
    VEC("vec");

    public final String key;

    DataType(String key) {
        this.key = key;
    }
}
