# -*- coding: utf-8 -*-
"""
从 micro-control-finance 静态站点提取正文，生成 RAG 知识库 JSON。
输出：knowledge-base.json（每条 = {section, title, chapter, content}）
"""
import json
import re
import html
from pathlib import Path
from html.parser import HTMLParser

SITE_ROOT = Path(r"c:\Work\Minimax_Work\micro-control-finance")
MANIFEST = SITE_ROOT / "assets" / "content-manifest.json"
OUTPUT = Path(r"c:\Work\Fincontrol-main\fincontrol-backend\src\main\resources\knowledge\knowledge-base.json")

CHAPTER_NAMES = {1: "理论渊源", 2: "架构与算法", 3: "实证、回测与子系统", 4: "工程实现", 5: "修正、危机与方法论"}


class MainContentExtractor(HTMLParser):
    """只提取 <main class="page"> 内的文本，保留标题层级和表格结构。"""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.in_main = False
        self.depth = 0
        self.skip_tags = {"script", "style", "nav", "header", "footer"}
        self.in_skip = 0
        self.current_tag = None
        self.parts = []
        # 表格收集
        self.in_table = False
        self.table_rows = []
        self.current_row = []
        self.current_cell = []
        self.in_cell = False

    def handle_starttag(self, tag, attrs):
        attrs_dict = dict(attrs)
        if tag == "main" and "page" in attrs_dict.get("class", ""):
            self.in_main = True
            return
        if not self.in_main:
            return
        if tag in self.skip_tags:
            self.in_skip += 1
            return
        if self.in_skip > 0:
            return
        self.current_tag = tag
        if tag in ("h1", "h2", "h3", "h4"):
            self.parts.append("\n\n## " if tag in ("h2", "h3", "h4") else "\n# ")
        elif tag == "p":
            self.parts.append("\n")
        elif tag == "li":
            self.parts.append("\n- ")
        elif tag == "br":
            self.parts.append("\n")
        elif tag == "table":
            self.in_table = True
            self.table_rows = []
        elif tag == "tr" and self.in_table:
            self.current_row = []
        elif tag in ("td", "th") and self.in_table:
            self.in_cell = True
            self.current_cell = []
        elif tag in ("strong", "b"):
            self.parts.append("**")

    def handle_endtag(self, tag):
        if not self.in_main:
            return
        if tag in self.skip_tags:
            self.in_skip = max(0, self.in_skip - 1)
            return
        if self.in_skip > 0:
            return
        if tag == "main":
            self.in_main = False
        elif tag in ("strong", "b"):
            self.parts.append("**")
        elif tag in ("td", "th") and self.in_table:
            self.in_cell = False
            cell_text = "".join(self.current_cell).strip()
            self.current_row.append(cell_text)
        elif tag == "tr" and self.in_table:
            if self.current_row:
                self.table_rows.append(self.current_row)
        elif tag == "table" and self.in_table:
            self.in_table = False
            # 表格转文本
            if self.table_rows:
                lines = []
                for row in self.table_rows:
                    lines.append(" | ".join(row))
                self.parts.append("\n" + "\n".join(lines) + "\n")
        elif tag in ("h1", "h2", "h3", "h4", "p", "li"):
            self.parts.append("\n")

    def handle_data(self, data):
        if not self.in_main or self.in_skip > 0:
            return
        text = data.strip()
        if not text:
            return
        if self.in_cell and self.in_table:
            self.current_cell.append(text)
        else:
            self.parts.append(text)

    def get_text(self):
        raw = "".join(self.parts)
        # 清理多余空白
        raw = re.sub(r"[ \t]+", " ", raw)
        raw = re.sub(r"\n{3,}", "\n\n", raw)
        return raw.strip()


def extract_main_text(html_path):
    text = html_path.read_text(encoding="utf-8")
    parser = MainContentExtractor()
    parser.feed(text)
    return parser.get_text()


def main():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    docs = []
    total = 0
    for ch in manifest["chapters"]:
        ch_num = ch["num"]
        ch_dir = SITE_ROOT / ch["dir"]
        # 章导论
        intro_path = ch_dir / "index.html"
        if intro_path.exists():
            content = extract_main_text(intro_path)
            if content:
                docs.append({
                    "section": f"第{ch_num}章导论",
                    "title": ch["title_long"].split("·")[-1].strip() if "·" in ch["title_long"] else ch["title_long"],
                    "chapter": ch_num,
                    "chapterName": CHAPTER_NAMES.get(ch_num, ""),
                    "content": content,
                })
                total += 1
        # 各节
        for sec in ch["sections"]:
            sec_path = ch_dir / f"{sec['slug']}.html"
            if sec_path.exists():
                content = extract_main_text(sec_path)
                if content:
                    docs.append({
                        "section": sec["num"],
                        "title": sec["title"],
                        "chapter": ch_num,
                        "chapterName": CHAPTER_NAMES.get(ch_num, ""),
                        "content": content,
                    })
                    total += 1

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(docs, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"提取完成：{total} 条知识条目 -> {OUTPUT}")
    # 统计
    lengths = [len(d["content"]) for d in docs]
    print(f"正文长度：最短 {min(lengths)} / 最长 {max(lengths)} / 平均 {sum(lengths)//len(lengths)} 字")


if __name__ == "__main__":
    main()
