import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const path = "C:/Users/29101/Downloads/test1_会话问题首包延时统计.xlsx";
const blob = await FileBlob.load(path);
const workbook = await SpreadsheetFile.importXlsx(blob);

const summary = await workbook.inspect({
  kind: "table",
  range: "汇总!A1:H21",
  include: "values,formulas",
  tableMaxRows: 25,
  tableMaxCols: 10,
  maxChars: 8000,
});
const detailTop = await workbook.inspect({
  kind: "table",
  range: "会话问题明细!A1:J16",
  include: "values,formulas",
  tableMaxRows: 18,
  tableMaxCols: 12,
  maxChars: 8000,
});
const detailBottom = await workbook.inspect({
  kind: "table",
  range: "会话问题明细!A78:J83",
  include: "values,formulas",
  tableMaxRows: 8,
  tableMaxCols: 12,
  maxChars: 5000,
});
const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan",
  maxChars: 4000,
});

console.log("SUMMARY\n" + summary.ndjson);
console.log("DETAIL_TOP\n" + detailTop.ndjson);
console.log("DETAIL_BOTTOM\n" + detailBottom.ndjson);
console.log("ERRORS\n" + errors.ndjson);
