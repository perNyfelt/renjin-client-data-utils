package se.alipsa.renjin.client.datautils;

import org.renjin.primitives.Types;
import org.renjin.primitives.vector.RowNamesVector;
import org.renjin.sexp.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

public class RDataTransformer {

  private RDataTransformer() {
    // Utiltity class
  }

  public static List<String> toHeaderList(ListVector df) {
    List<String> colList = new ArrayList<>();
    if (df.hasAttributes()) {
      AttributeMap attributes = df.getAttributes();
      Map<Symbol, SEXP> attrMap = attributes.toMap();
      Symbol s = attrMap.keySet().stream().filter(p -> "names".equals(p.getPrintName())).findAny().orElse(null);
      Vector colNames = (Vector) attrMap.get(s);
      for (int i = 0; i < colNames.length(); i++) {
        colList.add(colNames.getElementAsString(i));
      }
    }
    return colList;
  }

  public static List<DataType> toTypeList(ListVector df) {
    List<DataType> typeList = new ArrayList<>();
    for (SEXP col : df) {
      Vector column = (Vector) col;
      typeList.add(DataType.forVectorType(column.getVectorType()));
    }
    return typeList;
  }

  public static List<List<Object>> toRowlist(ListVector df) {
    List<Vector> table = new ArrayList<>();
    for (SEXP col : df) {
      Vector column = (Vector) col;
      table.add(column);
    }
    return transpose(table);
  }

  public static List<List<Object>> transpose(List<Vector> table) {
    List<List<Object>> ret = new ArrayList<>();
    final int N = table.get(0).length();
    for (int i = 0; i < N; i++) {
      List<Object> row = new ArrayList<>();
      for (Vector col : table) {
        getValue(col, row, i);
      }
      ret.add(row);
    }
    return ret;
  }

  private static void getValue(Vector col, List<Object> column, int i) {
    if (Types.isFactor(col)) {
      AttributeMap attributes = col.getAttributes();
      Map<Symbol, SEXP> attrMap = attributes.toMap();
      Symbol s = attrMap.keySet().stream().filter(p -> "levels".equals(p.getPrintName())).findAny().orElse(null);
      Vector vec = (Vector) attrMap.get(s);
      column.add(vec.getElementAsObject(col.getElementAsInt(i) - 1));
    } else {
      column.add(col.getElementAsObject(i));
    }
  }

  /**
   *
   * @param table the Table to convert
   * @param stringsOnlyOpt if true, the resulting ListVector (data.frame) will consist of Strings (characters)
   * @return
   */
  public static ListVector toDataFrame(Table table, boolean... stringsOnlyOpt) {
    List<Vector.Builder<?>> builders;
    if (stringsOnlyOpt.length > 0 && stringsOnlyOpt[0]) {
      builders = stringBuilders(table.headerList.size());
    } else {
      builders = builders(table);
    }
    int numRows = 0;

    for (int rowIdx = 0; rowIdx < table.rowList.size(); rowIdx++) {
      numRows++;
      List<Object> row = table.rowList.get(rowIdx);
      int i = 0;
      for (int colIdx = 0; colIdx < row.size(); colIdx++) {
        //System.out.println("Adding ext.getString(" + rowIdx + ", " + colIdx+ ") = " + ext.getString(row, colIdx));

        // Unfortunately Vector.Builder does not have an add(Object) method so we need to downcast
        Vector.Builder<?> builder = builders.get(i++);
        Object value = row.get(colIdx);
        if (value == null) {
          builder.addNA();
        } else if (builder instanceof IntArrayVector.Builder) {
          ((IntArrayVector.Builder)builder).add(asInteger(value));
        } else if (builder instanceof LogicalArrayVector.Builder) {
          ((LogicalArrayVector.Builder)builder).add(asBoolean(value));
        } else if (builder instanceof DoubleArrayVector.Builder) {
          ((DoubleArrayVector.Builder)builder).add(asDouble(value));
        } else if (builder instanceof StringVector.Builder) {
          ((StringVector.Builder)builder).add(asString(value));
        } else if (builder instanceof RawVector.Builder) {
          ((RawVector.Builder)builder).add(asByte(value));
        }
      }
    }
    ListVector columnVector = columnInfo(table.headerList);
    /* call build() on each column and add them as named cols to df */
    ListVector.NamedBuilder dfBuilder = new ListVector.NamedBuilder();
    for (int i = 0; i < columnVector.length(); i++) {
      ListVector ci = (ListVector) columnVector.get(i);
      dfBuilder.add(ci.get("name").asString(), builders.get(i).build());
    }
    dfBuilder.setAttribute("row.names", new RowNamesVector(numRows));
    dfBuilder.setAttribute("class", StringVector.valueOf("data.frame"));
    return dfBuilder.build();
  }

  private static byte asByte(@Nonnull Object value) {
    if (value instanceof Byte) {
      return (Byte)value;
    }
    return Byte.parseByte(String.valueOf(value));
  }

  private static String asString(@Nonnull Object value) {
    return String.valueOf(value);
  }

  private static double asDouble(@Nonnull Object value) {
    if (value instanceof Double) {
      return (Double)value;
    }
    return Double.parseDouble(String.valueOf(value));
  }

  private static boolean asBoolean(@Nonnull Object value) {
    if (value instanceof Boolean) {
      return (Boolean)value;
    }
    String stringValue = String.valueOf(value);
    return "true".equalsIgnoreCase(stringValue) || "1".equalsIgnoreCase(stringValue);
  }

  private static int asInteger(@Nonnull Object value) {
    if (value instanceof Integer) {
      return (Integer)value;
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private static ListVector columnInfo(List<String> headerList) {
    ListVector.Builder tv = new ListVector.Builder();
    for (String name : headerList) {
      ListVector.NamedBuilder cv = new ListVector.NamedBuilder();
      cv.add("name", name);
      tv.add(cv.build());
    }
    return tv.build();
  }

  private static List<Vector.Builder<?>> builders(Table table) {
    int numColNums = table.columnTypes.size();
    int numRows = table.getRowList().size();
    List<Vector.Builder<?>> builderList = new ArrayList<>(numColNums);
    for (int i = 0; i < numColNums; i++) {
      DataType dataType = table.columnTypes.get(i);
      builderList.add(dataType.getVectorType().newBuilderWithInitialCapacity(numRows));
    }
    return builderList;
  }


  private static List<Vector.Builder<?>> stringBuilders(int numColNums) {
    List<Vector.Builder<?>> builder = new ArrayList<>();
    for (int i = 0; i <= numColNums; i++) {
      builder.add(new StringVector.Builder());
    }
    //System.out.println("created " + builder.size() + " stringBuilders");
    return builder;
  }
}
