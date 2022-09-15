import java.util.ArrayList;
import java.util.List;

class Instruction {
	public String op;

	public String arg1;

	public String arg2;

	public String result;

	public Instruction(String op, String arg1, String arg2, String result) {
		this.op = op;
		this.arg1 = arg1;
		this.arg2 = arg2;
		this.result = result;
	}
}

public class TACGenerator {

	public int cx = 0;

	public List<Instruction> code = new ArrayList<>();

	public void gen(String op, String arg1, String arg2, String result) {
		code.add(new Instruction(op, arg1, arg2, result));
		cx ++;
	}

	public void print(){

		int c = 1;
		for (Instruction i: code){
			System.out.println("(" + c + ")(" + i.op + "," + i.arg1 + "," + i.arg2 + "," + i.result + ")");
			c ++;
		}
	}

}

