import java.io.*;


public class Test {

    public static void main(String[] args) throws IOException {

        BufferedReader fin = new BufferedReader(new FileReader("src/test/ParserTests/test5"), 4096);

        // 构造编译器并初始化
        PL0 pl0 = new PL0(fin);

        pl0.parser.nextSym();
        pl0.parser.parse();

    }
}
