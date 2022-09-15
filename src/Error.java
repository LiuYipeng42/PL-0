import java.util.*;
import java.util.stream.Collectors;


public class Error {

    private final HashMap<Integer, List<Integer>> errors = new HashMap<>();

    public void report(int errCode, int line) {

        List<Integer> errorsInLine = errors.getOrDefault(line, new ArrayList<>());

        errorsInLine.add(errCode);

        errors.put(line, errorsInLine);
    }

    public boolean isEmpty(){
        return errors.isEmpty();
    }

    public void printSemantic(){
        for (int line : errors.keySet().stream().sorted().collect(Collectors.toList()))
            System.out.println("(语义错误,行号:" + line + ")");
    }

    public void printSyntactic(){
        for (int line : errors.keySet().stream().sorted().collect(Collectors.toList()))
            System.out.println("(语法错误,行号:" + line + ")");
    }

    public void print(){
        errors.forEach((integer, integers) -> System.out.println("line:" + integer + " errors:" + integers));
    }

}
