import java.io.BufferedReader;
import java.io.IOException;


public class Scanner {
    /**
     * 刚刚读入的字符
     */
    private char ch = ' ';

    /**
     * 当前读入的行
     */
    private char[] line;
    public int lineNum;

    /**
     * 当前行的长度（line length）
     */
    public int ll = 0;

    /**
     * 当前字符在当前行中的位置（character counter）
     */
    public int cc = 0;

    /**
     * 当前读入的符号
     */
    public Symbol sym;

    /**
     * 保留字列表（注意保留字的存放顺序）
     */
    private final String[] word;

    /**
     * 保留字对应的符号值
     */
    private final Symbol[] wsym;

    /**
     * 单字符的符号值
     */
    private final Symbol[] ssym;

    // 输入流
    private final BufferedReader in;

    /**
     * 标识符名字（如果当前符号是标识符的话）
     */
    public String id;

    /**
     * 数值大小（如果当前符号是数字的话）
     */
    public int num;

    /**
     * 多行注释 flag
     */
    boolean mulComment = false;

    public String error;

    /**
     * 初始化词法分析器
     */
    public Scanner(BufferedReader input) {
        in = input;

        // 设置单字符符号
        ssym = new Symbol[256];
        java.util.Arrays.fill(ssym, Symbol.nul);
        ssym['+'] = Symbol.plus;
        ssym['-'] = Symbol.minus;
        ssym['*'] = Symbol.times;
        ssym['/'] = Symbol.slash;
        ssym['('] = Symbol.lparen;
        ssym[')'] = Symbol.rparen;
        ssym['='] = Symbol.eql;
        ssym[','] = Symbol.comma;
        ssym['.'] = Symbol.period;
        ssym['#'] = Symbol.neq;
        ssym[';'] = Symbol.semicolon;

        // 设置保留字名字,按照字母顺序，便于折半查找
        word = new String[]{"begin", "call", "const", "do", "end", "if",
                "odd", "procedure", "read", "then", "var", "while", "write"};

        // 设置保留字符号
        wsym = new Symbol[PL0.norw];
        wsym[0] = Symbol.beginSym;
        wsym[1] = Symbol.callSym;
        wsym[2] = Symbol.constSym;
        wsym[3] = Symbol.doSym;
        wsym[4] = Symbol.endSym;
        wsym[5] = Symbol.ifSym;
        wsym[6] = Symbol.oddSym;
        wsym[7] = Symbol.procSym;
        wsym[8] = Symbol.readSym;
        wsym[9] = Symbol.thenSym;
        wsym[10] = Symbol.varSym;
        wsym[11] = Symbol.whileSym;
        wsym[12] = Symbol.writeSym;
    }

    /**
     * 读取一个字符，为减少磁盘I/O次数，每次读取一行
     */
    void getChar() {
        String l;
        try {
            if (cc == ll || (line[cc] == '/' && (line[cc + 1] == '/' || line[cc + 1] == '*'))) {
                while (true) {
                    l = in.readLine().toLowerCase() + "\n";
                    lineNum ++;

                    if (l.charAt(0) == '/' && l.charAt(1) == '*') {
                        mulComment = true;
                    } else if (l.charAt(0) == '*' && l.charAt(1) == '/') {
                        mulComment = false;
                    } else if (!(l.equals("\n") || (l.charAt(0) == '/' && l.charAt(1) == '/'))){
                        if (!mulComment)
                            break;
                    }

                }
                ll = l.length();
                cc = 0;
                line = l.toCharArray();

            }
        } catch (IOException e) {
            throw new java.lang.Error("program incomplete");
        }
        ch = line[cc];
        cc++;
    }

    /**
     * 词法分析，获取一个词法符号，是词法分析器的重点
     */
    public void getSymbol() {
        error = "";
        while (Character.isWhitespace(ch))        // 跳过所有空白字符
            getChar();

        if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
            // 关键字或者一般标识符
            matchKeywordOrIdentifierOrNum();
        } else if ("+-*/()=,.#;<>:".contains(String.valueOf(ch))) {
            // 操作符
            matchOperator();
        }else {
            error = "非法字符(串)";
            sym = Symbol.ident;
            id = String.valueOf(ch);
            getChar();
        }
    }

    void matchKeywordOrIdentifierOrNum() {
        int i;
        boolean isNum = true;

        StringBuilder sb = new StringBuilder(PL0.idMax);
        // 首先把整个单词读出来
        do {
            if (ch >= 'a' && ch <= 'z'){
                isNum = false;
            }
            sb.append(ch);
            getChar();
        } while ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9'));

        if (isNum){
            sym = Symbol.number;
            num = Integer.parseInt(sb.toString());
            if (num > PL0.maxNum)
                error = "无符号整数越界";
        }else {
            id = sb.toString();
            if ('0' < id.charAt(0) && id.charAt(0) < '9')
                error = "非法字符(串)";

            // 然后搜索是不是保留字（请注意使用的是什么搜索方法）
            i = java.util.Arrays.binarySearch(word, id);

            // 最后形成符号信息
            if (i < 0) {
                // 一般标识符
                if (id.length() > PL0.idMax)
                    error = "标识符长度超长";
                sym = Symbol.ident;
            } else {
                // 关键字
                sym = wsym[i];
            }
        }
    }

    void matchOperator() {
        // 请注意这里的写法跟Wirth的有点不同
        switch (ch) {
            case ':':        // 赋值符号
                getChar();
                if (ch == '=') {
                    sym = Symbol.becomes;
                    getChar();
                } else {
                    // 不能识别的符号
                    sym = Symbol.nul;
                }
                break;
            case '<':        // 小于或者小于等于
                getChar();
                if (ch == '=') {
                    sym = Symbol.leq;
                    getChar();
                } else {
                    sym = Symbol.lss;
                }
                break;
            case '>':        // 大于或者大于等于
                getChar();
                if (ch == '=') {
                    sym = Symbol.geq;
                    getChar();
                } else {
                    sym = Symbol.gtr;
                }
                break;
            default:        // 其他为单字符操作符（如果符号非法则返回nil）
                sym = ssym[ch];

                if (sym != Symbol.period)
                    getChar();
                break;
        }
    }
}
