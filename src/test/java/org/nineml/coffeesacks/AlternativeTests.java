package org.nineml.coffeesacks;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;
import java.util.List;

// Just check that the algorithm for computing alternatives works
public class AlternativeTests {
    private int makeSelection(int value, int selected) {
        if (value != 0 && value != 1) { // 1 is the same as 0
            value--; // convert back to 0-based index
            // Map the number back to the "right" number in the un-reordered list
            return ((int) (value <= selected ? value - 1 : value));
        }
        return selected;
    }

    @Test
    public void select0() {
        List<Integer> list = Arrays.asList(2, 0, 1, 3, 4);
        Assertions.assertEquals(2, makeSelection(0, 2));
    }

    @Test
    public void select1() {
        List<Integer> list = Arrays.asList(2, 0, 1, 3, 4);
        Assertions.assertEquals(2, makeSelection(1, 2));
    }

    @Test
    public void select2() {
        List<Integer> list = Arrays.asList(2, 0, 1, 3, 4);
        Assertions.assertEquals(0, makeSelection(2, 2));
    }

    @Test
    public void select3() {
        List<Integer> list = Arrays.asList(2, 0, 1, 3, 4);
        Assertions.assertEquals(1, makeSelection(3, 2));
    }

    @Test
    public void select4() {
        List<Integer> list = Arrays.asList(2, 0, 1, 3, 4);
        Assertions.assertEquals(3, makeSelection(4, 2));
    }

    @Test
    public void select5() {
        List<Integer> list = Arrays.asList(2, 0, 1, 3, 4);
        Assertions.assertEquals(4, makeSelection(5, 0));
    }

}
