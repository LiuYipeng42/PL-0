import java.util.Objects;


public class Parser {
	public final Scanner lex;					// 对词法分析器的引用
	private final Table table;					// 对符号表的引用
	private final TACGenerator tac;				// 对目标代码生成器的引用
	private final Error errors;
	
	private final int symbolNum = Symbol.values().length;
	
	// 表示声明开始的符号集合、表示语句开始的符号集合、表示因子开始的符号集合
	// 实际上这就是声明、语句和因子的FIRST集合
	private final SymSet declareFirst;
	private final SymSet statementFirst;
	private final SymSet factorFirst;

	public Symbol sym;

	public int dx = 0;

	public Parser(Scanner lex, Table table, TACGenerator tac, Error errors) {
		this.lex = lex;
		this.table = table;
		this.tac = tac;
		this.errors = errors;

		// 设置声明开始符号集
		declareFirst = new SymSet(symbolNum);
		declareFirst.set(Symbol.constSym);
		declareFirst.set(Symbol.varSym);
		declareFirst.set(Symbol.procSym);

		// 设置语句开始符号集
		statementFirst = new SymSet(symbolNum);
		statementFirst.set(Symbol.beginSym);
		statementFirst.set(Symbol.callSym);
		statementFirst.set(Symbol.ifSym);
		statementFirst.set(Symbol.whileSym);
		statementFirst.set(Symbol.readSym);			// thanks to elu
		statementFirst.set(Symbol.writeSym);

		// 设置因子开始符号集
		factorFirst = new SymSet(symbolNum);
		factorFirst.set(Symbol.ident);
		factorFirst.set(Symbol.number);
		factorFirst.set(Symbol.lparen);

	}

	public void parse() {
		SymSet nxtLev = new SymSet(symbolNum);
		nxtLev.or(declareFirst);
		nxtLev.or(statementFirst);
		nxtLev.set(Symbol.period);

		tac.gen("syss", "_", "_", "_");
		parseBlock(0, nxtLev);
		tac.gen("syse", "_", "_", "_");

		if (sym != Symbol.period)
//			errors.report();
			errors.report(9, lex.lineNum);
	}

	public void nextSym() {
		lex.getSymbol();
		sym =lex.sym;
	}
	
	/**
	 * 测试当前符号是否合法
	 *
	 * @param s1 我们需要的符号
	 * @param s2 如果不是我们需要的，则需要一个补救用的集合
	 * @param errCode 错误号
	 */
	void test(SymSet s1, SymSet s2, int errCode) {
		// 在某一部分（如一条语句，一个表达式）将要结束时时我们希望下一个符号属于某集合
		//（该部分的后跟符号），test负责这项检测，并且负责当检测不通过时的补救措施，程
		// 序在需要检测时指定当前需要的符号集合和补救用的集合（如之前未完成部分的后跟符
		// 号），以及检测不通过时的错误号。
		if (!s1.get(sym)) {
			errors.report(errCode, lex.lineNum);
			// 当检测不通过时，不停获取符号，直到它属于需要的集合或补救的集合
			while (!s1.get(sym) && !s2.get(sym) && sym != Symbol.period) {
				nextSym();
			}
		}
	}
	
	/**
	 * 分析<分程序>
	 * 
	 * @param lev 当前分程序所在层
	 * @param follow 当前模块后跟符号集
	 */
	public void parseBlock(int lev, SymSet follow) {
		// <分程序> := [<常量说明部分>][<变量说明部分>][<过程说明部分>]<语句>
		
		int dx0, tx0;				// 保留初始dx，tx和cx
		SymSet nxtLev;
		
		dx0 = dx;						// 记录本层之前的数据量（以便恢复）
		dx = 3;
		tx0 = table.tx;					// 记录本层名字的初始位置（以便恢复）
		table.get(table.tx).adr = tac.cx;

		if (lev > PL0.levMax)
			errors.report(32, lex.lineNum);
		
		// 分析<说明部分>
		do {
			// <常量说明部分>
			if (sym == Symbol.constSym) {
				nextSym();
				parseConstDeclaration(lev);
				while (sym == Symbol.comma) {
					nextSym();
					parseConstDeclaration(lev);
				}

				if (sym != Symbol.semicolon)
					errors.report(5, lex.lineNum);				// 漏掉了逗号或者分号
				nextSym();

			}
			
			// <变量说明部分>
			if (sym == Symbol.varSym) {
				nextSym();
				parseVarDeclaration(lev);
				while (sym == Symbol.comma) {
					nextSym();
					parseVarDeclaration(lev);
				}

				if (sym != Symbol.semicolon)
					errors.report(5, lex.lineNum);				// 漏掉了逗号或者分号
				nextSym();
			}

			// <过程说明部分>
			while (sym == Symbol.procSym) {

				int wrong = 0;
				nextSym();
				if (sym == Symbol.ident) {
					tac.gen("procedure", lex.id, "_", "_");
					table.enter(SymType.procedure, lex.id, 0, lev, dx);
					nextSym();
				} else { 
					errors.report(4, lex.lineNum);				// procedure后应为标识符 10
					wrong ++;
				}

				if (sym == Symbol.semicolon)
					nextSym();
				else {
					errors.report(5, lex.lineNum);                // 漏掉了分号 10
					wrong ++;
				}

				nxtLev = (SymSet) follow.clone();
				nxtLev.set(Symbol.semicolon);
				parseBlock(lev+1, nxtLev);

				if (wrong < 2) {
					if (sym == Symbol.semicolon) {
						nextSym();
						nxtLev = (SymSet) statementFirst.clone();
						nxtLev.set(Symbol.ident);
						nxtLev.set(Symbol.procSym);
						test(nxtLev, follow, 6);
						tac.gen("ret", "_", "_", "_");
					} else {
						errors.report(5, lex.lineNum);                // 漏掉了分号 23
					}
				}

			}

			nxtLev = (SymSet) statementFirst.clone(); 
			nxtLev.set(Symbol.ident);
			test(nxtLev, declareFirst, 7);
		} while (declareFirst.get(sym));		// 直到没有声明符号
		
		// 开始生成当前过程代码
		Table.Item item = table.get(tx0);
		item.adr = tac.cx;					// 当前过程代码地址
		item.size = dx;							// 声明部分中每增加一条声明都会给dx增加1，
												// 声明部分已经结束，dx就是当前过程的堆栈帧大小
		// 分析<语句>
		nxtLev = (SymSet) follow.clone();		// 每个后跟符号集和都包含上层后跟符号集和，以便补救
		nxtLev.set(Symbol.semicolon);		// 语句后跟符号为分号或end
		nxtLev.set(Symbol.endSym);
		parseStatement(nxtLev, lev);

		nxtLev = new SymSet(symbolNum);	// 分程序没有补救集合

		test(follow, nxtLev, 8);				// 检测后跟符号正确性
		
		table.print(tx0);

		dx = dx0;							// 恢复堆栈帧计数器
		table.tx = tx0;						// 回复名字表位置

	}

	/**
	 * 分析<常量说明部分>
	 * @param lev 当前所在的层次
	 */
	void parseConstDeclaration(int lev) {
		String id;
		if (sym == Symbol.ident) {
			tac.gen("const", lex.id, "_", "_");
			id = lex.id;
			nextSym();
			if (sym == Symbol.eql || sym == Symbol.becomes) {
				if (sym == Symbol.becomes) 
					errors.report(1, lex.lineNum);			// 把 = 写成了 :=
				nextSym();
				if (sym == Symbol.number) {
					if (table.position(lex.id) == 0) {
						tac.gen("=", String.valueOf(lex.num), "_", id);
						table.enter(SymType.constant, id, lex.num, lev, dx);
					}else {
						errors.report(0, lex.lineNum);      // 标识符重复
					}
					nextSym();
				} else {
					errors.report(2, lex.lineNum);			// 常量说明 = 后应是数字
				}
			} else {
				errors.report(3, lex.lineNum);				// 常量说明标识后应是 =
			}
		} else {
			errors.report(4, lex.lineNum);					// const 后应是标识符
		}
	}

	/**
	 * 分析<变量说明部分>
	 * @param lev 当前层次
	 */
	void parseVarDeclaration(int lev) {
		if (sym == Symbol.ident) {
			// 填写名字表并改变堆栈帧计数器
			if (table.position(lex.id) == 0){
				tac.gen("var", lex.id, "_", "_");
				table.enter(SymType.variable, lex.id, 0, lev, dx);
				dx++;
			}else {
				errors.report(0, lex.lineNum);  			// 标识符重复
			}
			nextSym();
		} else {
			errors.report(4, lex.lineNum);					// var 后应是标识
		}
	}

	/**
	 * 分析<语句>
	 * @param follow 后跟符号集
	 * @param lev 当前层次
	 */
	void parseStatement(SymSet follow, int lev) {
		SymSet nxtLev;

		switch (sym) {
		case ident:
			parseAssignStatement(follow, lev);
			break;
		case readSym:
			parseReadStatement(follow);
			break;
		case writeSym:
			parseWriteStatement(follow, lev);
			break;
		case callSym:
			parseCallStatement();
			break;
		case ifSym:
			parseIfStatement(follow, lev);
			break;
		case beginSym:
			parseBeginStatement(follow, lev);
			break;
		case whileSym:
			parseWhileStatement(follow, lev);
			break;
		default:
			nxtLev = new SymSet(symbolNum);
			test(follow, nxtLev, 19);
			break;
		}
	}

	/**
	 * 分析<当型循环语句>
	 * @param follow 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseWhileStatement(SymSet follow, int lev) {
		SymSet nxtLev;
		int i;
		Instruction instruction;
		String op;

		nextSym();
		nxtLev = (SymSet) follow.clone();
		nxtLev.set(Symbol.doSym);				// 后跟符号为do
		parseCondition(nxtLev, lev);			// 分析<条件>

		instruction = tac.code.get(tac.code.size() - 1);
		instruction.result += (tac.code.size() + 2);

		i = tac.code.size();

		if (sym == Symbol.doSym)
			nextSym();
		else
			errors.report(18, lex.lineNum - 1);						// 缺少do

		parseStatement(follow, lev);				// 分析<语句>

		switch (instruction.op){
			case "j=":
				op = "j#";
				break;
			case "j#":
				op = "j=";
				break;
			case "j>":
				op = "j<=";
				break;
			case "j<=":
				op = "j>";
				break;
			case "j<":
				op = "j>=";
				break;
			case "j>=":
				op = "j<";
				break;
			default:
				op = "j";
		}

		tac.code.add(i, new Instruction(op,instruction.arg1,instruction.arg2, "$" + (tac.code.size() +2) ));
	}

	/**
	 * 分析<复合语句>
	 * @param follow 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseBeginStatement(SymSet follow, int lev) {
		SymSet nxtLev;
		
		nextSym();
		nxtLev = (SymSet) follow.clone();
		nxtLev.set(Symbol.semicolon);
		nxtLev.set(Symbol.endSym);
		parseStatement(nxtLev, lev);
		// 循环分析{; <语句>}，直到下一个符号不是语句开始符号或收到end
		while (statementFirst.get(sym) || sym == Symbol.semicolon) {
			if (sym == Symbol.semicolon)
				nextSym();
			else
				errors.report(10, lex.lineNum - 1);					// 缺少分号
			parseStatement(nxtLev, lev);
		}
		if (sym == Symbol.endSym)
			nextSym();
		else
			errors.report(17, lex.lineNum);						// 缺少end或分号

	}

	/**
	 * 分析<条件语句>
	 * @param follow 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseIfStatement(SymSet follow, int lev) {
		SymSet nxtLev;
		int i;
		
		nextSym();
		nxtLev = (SymSet) follow.clone();
		nxtLev.set(Symbol.thenSym);				// 后跟符号为then或do ???
		nxtLev.set(Symbol.doSym);
		parseCondition(nxtLev, lev);			// 分析<条件>

		i = tac.code.size() - 1;

		tac.code.get(i).result += tac.code.size();

		if (sym == Symbol.thenSym)
			nextSym();
		else
			errors.report(16, lex.lineNum - 1);						// 缺少then
		parseStatement(follow, lev);				// 处理then后的语句

	}

	/**
	 * 分析<过程调用语句>
	 */
	private void parseCallStatement() {
		int i;
		nextSym();
		if (sym == Symbol.ident) {
			i = table.position(lex.id);
			if (i == 0) {
				errors.report(11, lex.lineNum);					// 过程未找到
			} else {
				Table.Item item = table.get(i);
				if (item.kind == SymType.procedure)
					tac.gen("call", item.name, "_", "_");
				else
					errors.report(15, lex.lineNum);				// call后标识符应为过程
			}
			nextSym();
		} else {
			errors.report(14, lex.lineNum);						// call后应为标识符
		}
	}

	/**
	 * 分析<写语句>
	 * @param follow 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseWriteStatement(SymSet follow, int lev) {
		SymSet nxtLev;
		String arg1;

		nextSym();
		if (sym != Symbol.lparen)
			errors.report(0, lex.lineNum);

		do {
			nextSym();
			nxtLev = (SymSet) follow.clone();
			nxtLev.set(Symbol.rparen);
			nxtLev.set(Symbol.comma);
			arg1 = parseExpression(nxtLev, lev);
			tac.gen("write", arg1, "_", "_");
		} while (sym == Symbol.comma);

		if (sym == Symbol.rparen)
			nextSym();
		else
			errors.report(33, lex.lineNum);				// write()中应为完整表达式

	}

	/**
	 * 分析<读语句>
	 * @param follow 后跟符号集
	 */
	private void parseReadStatement(SymSet follow) {
		int i;
		
		nextSym();
		if (sym != Symbol.lparen)
			errors.report(34, lex.lineNum);					// 格式错误，应是左括号

		do {
			nextSym();
			if (sym == Symbol.ident)
				i = table.position(lex.id);
			else
				i = 0;

			if (i == 0) {
				errors.report(35, lex.lineNum);			// read()中应是声明过的变量名
			} else {
				Table.Item item = table.get(i);
				if (item.kind != SymType.variable) {
					errors.report(32, lex.lineNum);		// read()中的标识符不是变量
				} else {
					tac.gen("read", item.name, "_", "_");
				}
			}

			nextSym();
		} while (sym == Symbol.comma);
		
		if (sym == Symbol.rparen) {
			nextSym();
		} else {
			errors.report(33, lex.lineNum);					// 格式错误，应是右括号
			while (!follow.get(sym))
				nextSym();
		}
	}

	/**
	 * 分析<赋值语句>
	 * @param follow 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseAssignStatement(SymSet follow, int lev) {
		int i;
		SymSet nxtLev;
		
		i = table.position(lex.id);
		if (i > 0) {
			Table.Item item = table.get(i);
			if (item.kind == SymType.variable) {
				nextSym();
				if (sym == Symbol.becomes)
					nextSym();
				else
					errors.report(13, lex.lineNum);					// 没有检测到赋值符号
				nxtLev = (SymSet) follow.clone();
				parseExpression(nxtLev, lev);
				tac.code.get(tac.code.size() - 1).result = item.name;
			} else {
				errors.report(12, lex.lineNum);						// 赋值语句格式错误
			}
		} else {
			errors.report(11, lex.lineNum);							// 变量未找到
		}
	}

	/**
	 * 分析<表达式>
	 * @param follow 后跟符号集
	 * @param lev 当前层次
	 */
	private String parseExpression(SymSet follow, int lev) {
		SymSet nxtLev;
		String arg1;
		String arg2;
		
		// 分析[+|-]<项>
		if (sym == Symbol.plus || sym == Symbol.minus) {
			nextSym();
		}
		nxtLev = (SymSet) follow.clone();
		nxtLev.set(Symbol.plus);
		nxtLev.set(Symbol.minus);
		arg1 = parseTerm(nxtLev, lev);

		Symbol op = sym;
		// 分析{<加法运算符><项>}
		while (sym == Symbol.plus || sym == Symbol.minus) {
			nextSym();
			nxtLev = (SymSet) follow.clone();
			nxtLev.set(Symbol.plus);
			nxtLev.set(Symbol.minus);
			arg2 = parseTerm(nxtLev, lev);

			if (op == Symbol.plus)
				tac.gen("+", arg1, arg2, "T1");
			else
				tac.gen("-", arg1, arg2, "T1");
		}

		if (!Objects.equals(arg1, "T1")){
			return arg1;
		}else {
			return "T1";
		}
	}

	/**
	 * 分析<项>
	 * @param follow 后跟符号集
	 * @param lev 当前层次
	 */
	private String parseTerm(SymSet follow, int lev) {
		SymSet nxtLev;
		String arg1;
		String arg2;

		// 分析<因子>
		nxtLev = (SymSet) follow.clone();
		nxtLev.set(Symbol.times);
		nxtLev.set(Symbol.slash);
		arg1 = parseFactor(nxtLev, lev);

		Symbol op = sym;
		// 分析{<乘法运算符><因子>}
		while (sym == Symbol.times || sym == Symbol.slash) {
			nextSym();
			arg2 = parseFactor(nxtLev, lev);

			if (op == Symbol.times)
				tac.gen("*", arg1, arg2, "T1");
			else
				tac.gen("/", arg1, arg2, "T1");

			arg1 = "T1";

		}

		if (!Objects.equals(arg1, "T1")){
			return arg1;
		}else {
			return "T1";
		}
	}

	/**
	 * 分析<因子>
	 * @param follow 后跟符号集
	 * @param lev 当前层次
	 */
	private String parseFactor(SymSet follow, int lev) {
		SymSet nxtLev;
		
		test(factorFirst, follow, 24);			// 检测因子的开始符号

		if (factorFirst.get(sym)) {
			if (sym == Symbol.ident) {			// 因子为常量或变量

				int i = table.position(lex.id);
				if (i > 0) {
					Table.Item item = table.get(i);
					switch (item.kind) {
						case constant:			// 名字为常量
						case variable:			// 名字为变量
							nextSym();
							return item.name;
						case procedure:			// 名字为过程
							errors.report(21, lex.lineNum);				// 不能为过程
							break;
					}
				} else {
					errors.report(11, lex.lineNum);					// 标识符未声明
				}
				nextSym();
			} else if (sym == Symbol.number) {	// 因子为数 
				int num = lex.num;
				if (num > PL0.maxNum) {
					errors.report(31, lex.lineNum);
				}
				nextSym();
				return String.valueOf(num);
			} else if (sym == Symbol.lparen) {	// 因子为表达式
				nextSym();
				nxtLev = (SymSet) follow.clone();
				nxtLev.set(Symbol.rparen);
				parseExpression(nxtLev, lev);
				if (sym == Symbol.rparen)
					nextSym();
				else
					errors.report(22, lex.lineNum);					// 缺少右括号
			} else {
				// 做补救措施
				test(follow, factorFirst, 23);
			}
		}
		return "T";
	}

	/**
	 * 分析<条件>
	 * @param follow 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseCondition(SymSet follow, int lev) {
		SymSet nxtLev;
		String arg1;
		String arg2;
		Symbol op;
		
		if (sym == Symbol.oddSym) {
			// 分析 ODD<表达式>
			nextSym();
			parseExpression(follow, lev);
		} else {
			// 分析<表达式><关系运算符><表达式>
			nxtLev = (SymSet) follow.clone();
			nxtLev.set(Symbol.eql);
			nxtLev.set(Symbol.neq);
			nxtLev.set(Symbol.lss);
			nxtLev.set(Symbol.leq);
			nxtLev.set(Symbol.gtr);
			nxtLev.set(Symbol.geq);
			arg1 = parseExpression(nxtLev, lev);
			if (sym == Symbol.eql || sym == Symbol.neq 
					|| sym == Symbol.lss || sym == Symbol.leq
					|| sym == Symbol.gtr || sym == Symbol.geq) {
				op = sym;
				nextSym();
				arg2 = parseExpression(follow, lev);

				switch (op) {
					case eql:
						tac.gen("j=", arg1, arg2, "$");
						break;
					case neq:
						tac.gen("j#", arg1, arg2, "$");
						break;
					case lss:
						tac.gen("j<", arg1, arg2, "$");
						break;
					case geq:
						tac.gen("j>=", arg1, arg2, "$");
						break;
					case gtr:
						tac.gen("j>", arg1, arg2, "$");
						break;
					case leq:
						tac.gen("j<=", arg1, arg2, "$");
						break;
				}

			} else {
				errors.report(20, lex.lineNum);
			}
		}
	}
}
