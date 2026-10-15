package io.github.apertechprojects.queryforge.criteria;


import java.io.Serializable;

public enum CriteriaOperator implements Serializable {
    CONTAIN(1, "Contains (String)"),
    DOES_NOT_CONTAIN(2, "Does not contain (String)"),
    END_WITH(3, "Ends with (String)"),
    START_WITH(4, "Starts with (String)"),
    SPECIFIED(5, "'true' for not null, 'false' for null"),
    EQUAL(6, "Equals"),
    NOT_EQUAL(7, "Not equals"),
    GREATER_THAN(8, "Greater than"),
    GREATER_THAN_OR_EQUAL(9, "Greater than or equal"),
    LESS_THAN(10, "Less than"),
    LESS_THAN_OR_EQUAL(11, "Less than or equal"),
    OR(12, "Or (splits the criteria list at its position; key and values are ignored)"),
    PARENTHES(13, "Sub Criteria");

    private int value = -1;
    private String meaning;

    CriteriaOperator(int value, String meaning) {
        this.value = value;
        this.meaning = meaning;
    }

    public int getValue() {
        return value;
    }

    public String getMeaning() {
        return meaning;
    }
}
