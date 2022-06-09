package solution;

public class Solution {

    public static String interpret(final String input) {
        AssemblyInterpreter interpreter = new AssemblyInterpreter(input);
        return interpreter.getOutput();
    }
}
