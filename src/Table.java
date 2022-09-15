/**
 * 符号类型，为避免和Java的关键字Object冲突，我们改成 SymType
 */
enum SymType {
	constant, variable, procedure
}

public class Table {

	public StringBuilder tableInfo = new StringBuilder();

	public static class Item {
		String name;		// 名字
		SymType kind;		// 类型
		int val;			// 数值，仅const使用
		int level;			// 所处层，var和procedure使用
		int adr;			// 地址，var和procedure使用
		int size;			// 需要分配的数据区空间, 仅procedure使用
	}

	private final Item[] table = new Item[PL0.tableMax];
	
	/**
	 * 当前名字表项指针，也可以理解为当前有效的名字表大小（table size）
	 */
	public int tx = 0;
	
	/**
	 * 获得名字表某一项的内容
	 */
	public Item get(int i) {
		if (table[i] == null) {
			table[i] = new Item();
			table[i].name = "";
		}
		return table[i];
	}
	
	/**
	 * @param k   该符号的类型
	 * @param lev 名字所在的层次
	 * @param dx  当前应分配的变量的相对地址
	 */
	public void enter(SymType k, String id, int num, int lev, int dx) {
		tx ++;
		Item item = get(tx);
		item.name = id;
		item.kind = k;
		switch (k) {
		case constant:
			if (num > PL0.maxNum) {
				item.val = 0;
			} else {
				item.val = num;
			}
			break;
		case variable:
			item.level = lev;
			item.adr = dx;
			break;
		case procedure:
			item.level = lev;
			break;
		}
	}

	public void print(int start) {

		if (start < tx){
			tableInfo.append("符号表:\n");
			for (int i = start + 1; i <= tx; i++) {
				String msg = "OOPS! UNKNOWN TABLE ITEM!";
				switch (table[i].kind) {
					case constant:
						msg = "const " + table[i].name + " " + table[i].val;
						break;
					case variable:
						msg = "var " + table[i].name + " " + table[i].val;
						break;
					case procedure:
						msg = "procedure " + table[i].name;
						break;
				}
				tableInfo.append(msg).append("\n");
			}
		}
	}

	/**
	 * 在名字表中查找某个名字的位置
	 */
	public int position(String idt) {
		for (int i = tx; i > 0; i--)
			if (get(i).name.equals(idt))
				return i;
		
		return 0;
	}
}
