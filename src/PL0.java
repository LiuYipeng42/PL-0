import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;


public class PL0 {
	// 编译程序的常数
	public static final int idMax = 8;			// 符号的最大长度
	public static final int maxNum = 2047;		// 最大允许的数值
	public static final int levMax = 3;			// 最大允许过程嵌套声明层数 [0, levmax]
	public static final int norw = 32;			// 关键字个数
	public static final int tableMax = 100;		// 名字表容量

	public Parser parser;
	public Table table;
	public TACGenerator tac;
	public Error errors;

	/**
	 * 构造函数，初始化编译器所有组成部分
	 */
	public PL0(BufferedReader fin) {
		// 各部件的构造函数中都含有C语言版本的 init() 函数的一部分代码
		Scanner lex = new Scanner(fin);
		table = new Table();
		tac = new TACGenerator();
		errors = new Error();
		parser = new Parser(lex, table, tac, errors);
	}

	void compile() {
		boolean abort = false;
		
		try {

			parser.nextSym();		// 前瞻分析需要预先读入一个符号
			parser.parse();			// 开始语法分析过程（连同语法检查、目标代码生成）
		} catch (Exception e) {
			// 如果是发生严重错误则直接中止
			abort = true;
		}
		if (abort)
			System.exit(0);

	}

	public static void main(String[] args) {

		BufferedReader fin;
		try {

			fin = new BufferedReader(new FileReader("src/test/SemanticTests/test1"), 4096);

			PL0 pl0 = new PL0(fin);

			pl0.compile();

			if (pl0.errors.isEmpty()){
				System.out.println("语义正确");
				System.out.println("中间代码:");
				pl0.tac.print();
				System.out.println(pl0.table.tableInfo);
			}else {
				pl0.errors.printSemantic();
			}
			
		} catch (IOException e) {
			System.out.println("Can't open file!");
		}

	}
}
