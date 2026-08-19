import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const dataPath = "C:/Users/29101/.codex/visualizations/2026/08/07/019fdae0-9443-7ad3-8b2b-ce63cda40b4c/trace_stats/data.json";
const outputDir = "C:/Users/29101/.codex/visualizations/2026/08/07/019fdae0-9443-7ad3-8b2b-ce63cda40b4c/outputs/trace_stats";
const downloadPath = "C:/Users/29101/Downloads/test1_会话问题首包延时统计.xlsx";

const payload = JSON.parse(await fs.readFile(dataPath, "utf8"));
const runs = payload.runs ?? [];

const grouped = [];
const groupMap = new Map();
for (const run of runs) {
  const conversationId = String(run.conversationId ?? "");
  if (!groupMap.has(conversationId)) {
    const group = {
      conversationId,
      title: run.sessionTitle || "未命名会话",
      items: [],
    };
    groupMap.set(conversationId, group);
    grouped.push(group);
  }
  groupMap.get(conversationId).items.push(run);
}

const workbook = Workbook.create();
const summary = workbook.worksheets.add("汇总");
const detail = workbook.worksheets.add("会话问题明细");
summary.showGridLines = false;
detail.showGridLines = false;

const navy = "#16324F";
const teal = "#0F766E";
const lightTeal = "#DFF4F1";
const lightBlue = "#EAF2F8";
const gray = "#64748B";
const lightGray = "#E2E8F0";
const white = "#FFFFFF";

// 汇总页
summary.getRange("A1:H1").merge();
summary.getRange("A1").values = [["test1 会话问题与首包延时统计"]];
summary.getRange("A2:H2").merge();
summary.getRange("A2").values = [["按当前已完成会话与 Trace 数据统计｜首包延时来源：ttftMs"]];
summary.getRange("A1:H2").format = {
  fill: navy,
  font: { name: "Microsoft YaHei", color: white },
  verticalAlignment: "center",
};
summary.getRange("A1").format.font = { name: "Microsoft YaHei", color: white, bold: true, size: 18 };
summary.getRange("A2").format.font = { name: "Microsoft YaHei", color: "#D8E7F2", size: 10 };
summary.getRange("A1:H1").format.rowHeight = 32;
summary.getRange("A2:H2").format.rowHeight = 24;

const cards = [
  ["A4:B4", "A5:B6", "会话数", `=COUNTA(A10:A${9 + grouped.length})`, "0"],
  ["C4:D4", "C5:D6", "问题总数", "=COUNT('会话问题明细'!$E$5:$E$200)", "0"],
  ["E4:F4", "E5:F6", "平均首包延时", "=AVERAGE('会话问题明细'!$E$5:$E$200)", "0.0\" ms\""],
  ["G4:H4", "G5:H6", "最大首包延时", "=MAX('会话问题明细'!$E$5:$E$200)", "0\" ms\""],
];
for (const [labelRange, valueRange, label, formula, format] of cards) {
  summary.getRange(labelRange).merge();
  summary.getRange(labelRange.split(":")[0]).values = [[label]];
  summary.getRange(valueRange).merge();
  summary.getRange(valueRange.split(":")[0]).formulas = [[formula]];
  summary.getRange(labelRange).format = {
    fill: teal,
    font: { name: "Microsoft YaHei", color: white, bold: true, size: 10 },
    horizontalAlignment: "center",
    verticalAlignment: "center",
  };
  summary.getRange(valueRange).format = {
    fill: lightTeal,
    font: { name: "Microsoft YaHei", color: navy, bold: true, size: 18 },
    horizontalAlignment: "center",
    verticalAlignment: "center",
    numberFormat: format,
    borders: { preset: "outside", style: "thin", color: "#9CCFC8" },
  };
}

summary.getRange("A8:G8").merge();
summary.getRange("A8").values = [["分会话统计"]];
summary.getRange("A8:G8").format = {
  fill: navy,
  font: { name: "Microsoft YaHei", color: white, bold: true, size: 12 },
  verticalAlignment: "center",
};
summary.getRange("A9:G9").values = [[
  "会话序号", "会话名称", "问题数", "平均首包延时（ms）", "最小值（ms）", "最大值（ms）", "平均值（秒）",
]];
summary.getRange("A9:G9").format = {
  fill: teal,
  font: { name: "Microsoft YaHei", color: white, bold: true },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "outside", style: "thin", color: "#0B5F58" },
};

const summaryStart = 10;
const detailSessionRange = "$A$5:$A$200";
const detailTtftRange = "$E$5:$E$200";
for (let i = 0; i < grouped.length; i++) {
  const row = summaryStart + i;
  summary.getRange(`A${row}:B${row}`).values = [[i + 1, grouped[i].title]];
  summary.getRange(`C${row}:G${row}`).formulas = [[
    `=COUNTIF('会话问题明细'!${detailSessionRange},A${row})`,
    `=AVERAGEIF('会话问题明细'!${detailSessionRange},A${row},'会话问题明细'!${detailTtftRange})`,
    `=MINIFS('会话问题明细'!${detailTtftRange},'会话问题明细'!${detailSessionRange},A${row})`,
    `=MAXIFS('会话问题明细'!${detailTtftRange},'会话问题明细'!${detailSessionRange},A${row})`,
    `=D${row}/1000`,
  ]];
}
const summaryEnd = summaryStart + grouped.length - 1;
summary.getRange(`A${summaryStart}:G${summaryEnd}`).format = {
  font: { name: "Microsoft YaHei", size: 10 },
  verticalAlignment: "center",
  borders: { insideHorizontal: { style: "thin", color: lightGray }, bottom: { style: "thin", color: lightGray } },
};
summary.getRange(`A${summaryStart}:A${summaryEnd}`).format.horizontalAlignment = "center";
summary.getRange(`C${summaryStart}:G${summaryEnd}`).format.horizontalAlignment = "right";
summary.getRange(`C${summaryStart}:C${summaryEnd}`).format.numberFormat = "0";
summary.getRange(`D${summaryStart}:F${summaryEnd}`).format.numberFormat = "0.0";
summary.getRange(`G${summaryStart}:G${summaryEnd}`).format.numberFormat = "0.000";
summary.getRange(`A${summaryStart}:G${summaryEnd}`).conditionalFormats.add("Custom", {
  formula: `=MOD($A${summaryStart},2)=0`,
  format: { fill: "#F8FAFC" },
});

const noteRow = summaryEnd + 3;
summary.getRange(`A${noteRow}:H${noteRow + 1}`).merge();
summary.getRange(`A${noteRow}`).values = [["说明：首包延时为服务端 Trace 记录中的 ttftMs；明细表按会话首次提问时间和会话内提问顺序排列。"]];
summary.getRange(`A${noteRow}:H${noteRow + 1}`).format = {
  fill: lightBlue,
  font: { name: "Microsoft YaHei", color: gray, italic: true, size: 9 },
  wrapText: true,
  verticalAlignment: "center",
  borders: { preset: "outside", style: "thin", color: "#C6D7E4" },
};

summary.freezePanes.freezeRows(2);
summary.getRange("A:A").format.columnWidth = 11;
summary.getRange("B:B").format.columnWidth = 30;
summary.getRange("C:C").format.columnWidth = 12;
summary.getRange("D:F").format.columnWidth = 20;
summary.getRange("G:G").format.columnWidth = 16;
summary.getRange("H:H").format.columnWidth = 14;
summary.getRange(`A9:G${summaryEnd}`).format.rowHeight = 24;

// 明细页
detail.getRange("A1:J1").merge();
detail.getRange("A1").values = [["按会话分组的问题与首包延时明细"]];
detail.getRange("A2:J2").merge();
detail.getRange("A2").values = [[`共 ${grouped.length} 个会话、${runs.length} 个问题｜单位：毫秒 / 秒`]];
detail.getRange("A1:J2").format = {
  fill: navy,
  font: { name: "Microsoft YaHei", color: white },
  verticalAlignment: "center",
};
detail.getRange("A1").format.font = { name: "Microsoft YaHei", color: white, bold: true, size: 18 };
detail.getRange("A2").format.font = { name: "Microsoft YaHei", color: "#D8E7F2", size: 10 };
detail.getRange("A1:J1").format.rowHeight = 32;
detail.getRange("A2:J2").format.rowHeight = 24;
detail.getRange("A4:J4").values = [[
  "会话序号", "会话名称", "会话内序号", "提问内容", "首包延时（ms）", "首包延时（秒）", "Trace ID", "会话 ID", "提问时间", "状态",
]];
detail.getRange("A4:J4").format = {
  fill: teal,
  font: { name: "Microsoft YaHei", color: white, bold: true, size: 10 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "outside", style: "thin", color: "#0B5F58" },
};
detail.getRange("A4:J4").format.rowHeight = 30;

const detailRows = [];
const groupHeaderRows = [];
const dataSheetRows = [];
let sheetRow = 5;
for (let i = 0; i < grouped.length; i++) {
  const group = grouped[i];
  detailRows.push([`会话 ${i + 1}｜${group.title}｜${group.items.length} 个问题`, null, null, null, null, null, null, null, null, null]);
  groupHeaderRows.push(sheetRow);
  sheetRow += 1;
  for (let j = 0; j < group.items.length; j++) {
    const run = group.items[j];
    detailRows.push([
      i + 1,
      group.title,
      j + 1,
      run.question || "",
      Number(run.ttftMs),
      null,
      `'${String(run.traceId || "")}`,
      `'${String(run.conversationId || "")}`,
      run.startTime ? new Date(run.startTime) : null,
      run.status || "",
    ]);
    dataSheetRows.push(sheetRow);
    sheetRow += 1;
  }
}

const detailEnd = 4 + detailRows.length;
detail.getRange(`A5:J${detailEnd}`).values = detailRows;
for (const row of groupHeaderRows) {
  detail.getRange(`A${row}:J${row}`).merge();
  detail.getRange(`A${row}:J${row}`).format = {
    fill: lightBlue,
    font: { name: "Microsoft YaHei", color: navy, bold: true, size: 11 },
    verticalAlignment: "center",
    borders: { bottom: { style: "medium", color: "#9FB8CC" } },
  };
  detail.getRange(`A${row}:J${row}`).format.rowHeight = 24;
}

const formulaMatrix = [];
for (let row = 5; row <= detailEnd; row++) {
  formulaMatrix.push([dataSheetRows.includes(row) ? `=E${row}/1000` : null]);
}
detail.getRange(`F5:F${detailEnd}`).formulas = formulaMatrix;

for (const row of dataSheetRows) {
  detail.getRange(`A${row}:J${row}`).format = {
    font: { name: "Microsoft YaHei", size: 9 },
    verticalAlignment: "center",
    borders: { bottom: { style: "thin", color: lightGray } },
  };
  detail.getRange(`D${row}`).format.wrapText = true;
  detail.getRange(`A${row}:C${row}`).format.horizontalAlignment = "center";
  detail.getRange(`E${row}:F${row}`).format.horizontalAlignment = "right";
  detail.getRange(`G${row}:J${row}`).format.horizontalAlignment = "center";
  detail.getRange(`A${row}:J${row}`).format.rowHeight = 34;
}

detail.getRange(`E5:E${detailEnd}`).format.numberFormat = "0";
detail.getRange(`F5:F${detailEnd}`).format.numberFormat = "0.000";
detail.getRange(`G5:H${detailEnd}`).format.numberFormat = "@";
detail.getRange(`I5:I${detailEnd}`).format.numberFormat = "yyyy-mm-dd hh:mm:ss";
detail.getRange(`E5:E${detailEnd}`).conditionalFormats.add("colorScale", {
  colors: ["#DCFCE7", "#FDE68A", "#FCA5A5"],
  thresholds: ["min", "50%", "max"],
});

detail.freezePanes.freezeRows(4);
detail.getRange("A:A").format.columnWidth = 11;
detail.getRange("B:B").format.columnWidth = 25;
detail.getRange("C:C").format.columnWidth = 12;
detail.getRange("D:D").format.columnWidth = 72;
detail.getRange("E:E").format.columnWidth = 17;
detail.getRange("F:F").format.columnWidth = 16;
detail.getRange("G:H").format.columnWidth = 23;
detail.getRange("I:I").format.columnWidth = 22;
detail.getRange("J:J").format.columnWidth = 12;

await fs.mkdir(outputDir, { recursive: true });
const summaryPreview = await workbook.render({ sheetName: "汇总", autoCrop: "all", scale: 1, format: "png" });
await fs.writeFile(`${outputDir}/summary.png`, new Uint8Array(await summaryPreview.arrayBuffer()));
const detailPreview = await workbook.render({ sheetName: "会话问题明细", range: `A1:J${detailEnd}`, scale: 1, format: "png" });
await fs.writeFile(`${outputDir}/detail.png`, new Uint8Array(await detailPreview.arrayBuffer()));

const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(`${outputDir}/test1_会话问题首包延时统计.xlsx`);
await xlsx.save(downloadPath);

console.log(JSON.stringify({ output: downloadPath, backup: `${outputDir}/test1_会话问题首包延时统计.xlsx`, summaryPreview: `${outputDir}/summary.png`, detailPreview: `${outputDir}/detail.png`, detailEnd, sessions: grouped.length, questions: runs.length }));
