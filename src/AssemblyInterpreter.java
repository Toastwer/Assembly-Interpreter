import java.util.*;

public class AssemblyInterpreter {
    private final String input;

    private final HashMap<String, Integer> labels;
    private final HashMap<String, Integer> registers;
    private final Stack<Integer> ret;

    private String output;

    private enum Function {
        MOV,
        INC,
        DEC,
        ADD,
        SUB,
        MUL,
        DIV,
        JMP,
        CMP,
        JNE,
        JE,
        JGE,
        JG,
        JLE,
        JL,
        CALL,
        RET,
        MSG,
        END,
        NULL
    }

    private Comparison comparison;
    private int pointer;

    public AssemblyInterpreter(final String input) {
        this.input = input;

        labels = new HashMap<>();
        registers = new HashMap<>();
        ret = new Stack<>();

        interpret();
    }

    private void interpret() {
        String[] instructions = input.split("\n");

        cleanInput(instructions);

        final StringBuilder outputAccumulator = new StringBuilder();
        for (pointer = 0; pointer < instructions.length; pointer++) {
            if (instructions[pointer] == null) continue;

            Instruction instruction = new Instruction(instructions[pointer], outputAccumulator);
            if (executeInstruction(instruction)) {
                output = outputAccumulator.toString();
                break;
            }
        }
    }

    private void cleanInput(String[] instructions) {
        for (int i = 0; i < instructions.length; i++) {
            // Remove comments
            char[] chars = instructions[i].toCharArray();
            for (int j = 0; j < chars.length; j++) {
                if (chars[j] == ';') {
                    instructions[i] = instructions[i].substring(0, j);
                    break;
                }
            }

            // Set empty lines to null
            if (instructions[i].isEmpty()) {
                instructions[i] = null;
                continue;
            }

            // Store function (label) locations
            if (instructions[i].matches("\\w+:")) {
                labels.put(instructions[i].trim().substring(0, instructions[i].length() - 1), i);
            }
        }
    }

    private boolean executeInstruction(Instruction instruction) {
        switch (instruction.function) {
            case MOV -> setRegister(instruction.args[0], instruction.args[1]);
            case INC -> setRegister(instruction.args[0], getRegister(instruction.args[0]) + 1);
            case DEC -> setRegister(instruction.args[0], getRegister(instruction.args[0]) - 1);
            case ADD -> setRegister(instruction.args[0], getRegister(instruction.args[0]) + getConstOrRegister(instruction.args[1]));
            case SUB -> setRegister(instruction.args[0], getRegister(instruction.args[0]) - getConstOrRegister(instruction.args[1]));
            case MUL -> setRegister(instruction.args[0], getRegister(instruction.args[0]) * getConstOrRegister(instruction.args[1]));
            case DIV -> setRegister(instruction.args[0], getRegister(instruction.args[0]) / getConstOrRegister(instruction.args[1]));
            case JMP -> pointer = getLabelLocation(instruction.args[0]);
            case CMP -> comparison = new Comparison(getConstOrRegister(instruction.args[0]), getConstOrRegister(instruction.args[1]));
            case JNE -> comparison.jumpIf(Comparison.Comparator.NOT_EQUAL, getLabelLocation(instruction.args[0]));
            case JE -> comparison.jumpIf(Comparison.Comparator.EQUAL, getLabelLocation(instruction.args[0]));
            case JGE -> comparison.jumpIf(Comparison.Comparator.GREATER_OR_EQUAL, getLabelLocation(instruction.args[0]));
            case JG -> comparison.jumpIf(Comparison.Comparator.GREATER, getLabelLocation(instruction.args[0]));
            case JLE -> comparison.jumpIf(Comparison.Comparator.LESS_OR_EQUAL, getLabelLocation(instruction.args[0]));
            case JL -> comparison.jumpIf(Comparison.Comparator.LESS, getLabelLocation(instruction.args[0]));
            case CALL -> {
                ret.push(pointer);
                pointer = getLabelLocation(instruction.args[0]);
            }
            case RET -> pointer = ret.pop();
            case MSG -> addMessage(instruction.output, instruction.args);
            case END -> {
                return true;
            }
        }

        return false;
    }

    private void setRegister(String target, int value) {
        registers.put(target, value);
    }

    private void setRegister(String target, String value) {
        // We assume that if the parameter value is not a constant, it is a reference to a register.
        if (value.matches("-?\\d+")) {
            registers.put(target, Integer.parseInt(value));
        } else {
            registers.put(target, registers.get(value));
        }
    }

    private int getRegister(String reg) {
        if (registers.containsKey(reg)) {
            return registers.get(reg);
        }

        throw new RuntimeException("Register " + reg + " was fetched but doesn't exist.");
    }

    private int getConstOrRegister(String str) {
        if (str.matches("-?\\d+")) {
            return Integer.parseInt(str);
        } else {
            if (registers.containsKey(str)) {
                return registers.get(str);
            }

            throw new RuntimeException("Register " + str + " was fetched but doesn't exist.");
        }
    }

    private int getLabelLocation(String label) {
        if (labels.containsKey(label)) {
            return labels.get(label);
        }

        throw new RuntimeException("Label " + label + " was fetched but doesn't exist.");
    }

    private void addMessage(StringBuilder output, String[] parts) {
        for (String part : parts) {
            if (part.matches("'.*'")) {
                output.append(part, 1, part.length() - 1);
            } else {
                output.append(getConstOrRegister(part));
            }
        }
    }

    public String getOutput() {
        return output;
    }

    private static class Instruction {
        private final Function function;
        private final String[] args;
        private final StringBuilder output;

        private Instruction(String line, StringBuilder output) {
            this.output = output;

            LinkedList<String> parts = splitInstruction(line);

            function = getFunction(parts.pollFirst());
            args = parts.toArray(new String[0]);
        }

        private Function getFunction(String str) {
            for (Function function : Function.values()) {
                if (function.toString().equals(str.toUpperCase())) {
                    return function;
                }
            }

            return Function.NULL;
        }

        private LinkedList<String> splitInstruction(String raw) {
            LinkedList<String> instructions = new LinkedList<>();

            StringBuilder accumulator = new StringBuilder();

            boolean inString = false;
            for (char c : raw.toCharArray()) {
                if (c == '\'') {
                    inString = !inString;
                    accumulator.append(c);
                } else if (!inString) {
                    if (c == ' ' || c == ',') {
                        if (!accumulator.isEmpty()) {
                            instructions.addLast(accumulator.toString());
                            accumulator.setLength(0);
                        }
                    } else {
                        accumulator.append(c);
                    }
                } else {
                    accumulator.append(c);
                }
            }

            if (!accumulator.isEmpty()) {
                instructions.addLast(accumulator.toString());
            }

            return instructions;
        }
    }

    private final class Comparison {
        private final int val1;
        private final int val2;

        private Comparison(int val1, int val2) {
            this.val1 = val1;
            this.val2 = val2;
        }

        private enum Comparator {
            NOT_EQUAL,
            EQUAL,
            GREATER_OR_EQUAL,
            GREATER,
            LESS_OR_EQUAL,
            LESS
        }

        private void jumpIf(Comparator comparator, int pointerLocation) {
            switch (comparator) {
                case NOT_EQUAL -> {
                    if (val1 != val2) pointer = pointerLocation;
                }
                case EQUAL -> {
                    if (val1 == val2) pointer = pointerLocation;
                }
                case GREATER_OR_EQUAL -> {
                    if (val1 >= val2) pointer = pointerLocation;
                }
                case GREATER -> {
                    if (val1 > val2) pointer = pointerLocation;
                }
                case LESS_OR_EQUAL -> {
                    if (val1 <= val2) pointer = pointerLocation;
                }
                case LESS -> {
                    if (val1 < val2) pointer = pointerLocation;
                }
                default -> throw new IllegalStateException("Unexpected value: " + comparator);
            }
        }

        public int val1() {
            return val1;
        }

        public int val2() {
            return val2;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Comparison) obj;
            return this.val1 == that.val1 &&
                   this.val2 == that.val2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(val1, val2);
        }

        @Override
        public String toString() {
            return "Comparison[" +
                   "val1=" + val1 + ", " +
                   "val2=" + val2 + ']';
        }

    }
}
