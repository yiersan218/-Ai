// 把 CSV 源文本解析后转成 GFM markdown 表格，交由公共 MarkdownRenderer 渲染
// CSV 无样式，转 markdown 表格即可无损还原结构，避免再引一个重型表格组件

// 按 RFC4180 解析 CSV：支持引号包裹字段、字段内逗号/换行、双引号转义("")
function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let inQuotes = false;

  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        field += ch;
      }
      continue;
    }
    if (ch === '"') {
      inQuotes = true;
    } else if (ch === ",") {
      row.push(field);
      field = "";
    } else if (ch === "\n" || ch === "\r") {
      // 吃掉 \r\n 中的 \n，避免空行
      if (ch === "\r" && text[i + 1] === "\n") {
        i++;
      }
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else {
      field += ch;
    }
  }
  // 收尾最后一个字段/行(无末尾换行时)
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

function escapeCell(cell: string): string {
  return (cell ?? "")
    .replace(/\|/g, "\\|")
    .replace(/\r\n|\r|\n/g, "<br>");
}

function buildRow(cells: string[], width: number): string {
  const padded = [...cells];
  while (padded.length < width) {
    padded.push("");
  }
  return "| " + padded.map(escapeCell).join(" | ") + " |";
}

export function csvToMarkdown(text: string): string {
  const rows = parseCsv(text);
  if (rows.length === 0) {
    return "";
  }
  const width = rows.reduce((max, r) => Math.max(max, r.length), 0);
  const lines: string[] = [];
  lines.push(buildRow(rows[0], width));
  lines.push("| " + Array(width).fill("---").join(" | ") + " |");
  for (let i = 1; i < rows.length; i++) {
    lines.push(buildRow(rows[i], width));
  }
  return lines.join("\n");
}
