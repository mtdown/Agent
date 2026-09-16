package com.et.cloud.rag;

/**
 * Deterministic Chinese Markdown fixture used to prove the chunking profile refactor does not
 * drift the Chinese baseline. Built once, referenced by both the snapshot probe and the
 * permanent regression test, so the two can never disagree about what "unchanged" means.
 *
 * <p>Covers every branch of {@link MarkdownChunker}: front matter, heading hierarchy, an
 * oversized section split by paragraph, a tiny section merged forward, a single paragraph
 * that exceeds the cap (hard split on {@code 。} / {@code ；}), and a document number.
 */
final class ChineseChunkFixture {

    static final String CONTENT = build();

    private ChineseChunkFixture() {
    }

    private static String build() {
        StringBuilder md = new StringBuilder();
        md.append("---\n");
        md.append("title: \"重庆市困难群众救助补助资金管理办法\"\n");
        md.append("fileNum: \"渝府办发〔2026〕24号\"\n");
        md.append("---\n\n");
        md.append("# 重庆市困难群众救助补助资金管理办法\n\n");
        md.append("为规范困难群众救助补助资金管理，提高资金使用效益，根据国家有关规定，结合本市实际，制定本办法。\n\n");

        md.append("## 第一章 总则\n\n");
        md.append("第一条 本办法所称救助补助资金，是指中央和市级财政安排的用于保障困难群众基本生活的专项资金。\n\n");
        md.append("第二条 资金管理应当遵循公开、公平、公正的原则，坚持专款专用、讲求绩效。\n\n");

        md.append("## 第二章 补助标准\n\n");
        md.append("第四条 补助标准按照下列规定执行：\n\n");
        // oversized section: many medium paragraphs so the sum blows past the cap
        for (int i = 0; i < 14; i++) {
            md.append("第").append(i + 5).append("条 各区县人民政府应当根据本地经济社会发展水平和财力状况，"
                    + "合理确定本行政区域内困难群众救助补助的具体标准，并报市级主管部门备案。\n\n");
        }
        // single paragraph far longer than the cap, with sentence boundaries inside
        md.append("第十二条 ");
        for (int i = 0; i < 24; i++) {
            md.append("申请人应当如实提供家庭收入、财产状况等证明材料；");
            md.append("经办机构应当自受理之日起二十个工作日内完成核查并作出决定。");
        }
        md.append("\n\n");

        md.append("### (二)发放方式\n\n");
        md.append("通过银行代发到个人账户。\n\n");

        md.append("## 第三章 监督管理\n\n");
        md.append("第二十条 财政部门、民政部门应当加强对资金使用情况的监督检查，发现问题的，"
                + "应当及时督促整改；情节严重的，依法追究相关人员责任。\n\n");
        return md.toString();
    }
}
