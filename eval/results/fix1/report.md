# Piper eval: fix1

Commit `4ec6274-dirty`, chat model `gpt-6-luna` (temperature 0.0, seed 42), embeddings `text-embedding-3-small`, judge `pending`. 50 cases (34 RAG), started 2026-10-28T12:39:24.095030008-05:00.

## Summary

| Metric | Value | n |
|---|---|---|
| Tool pass (right tools, no forbidden call, exact args) | 0.980 | 50 |
| Tool selection accuracy | 0.980 | 50 |
| Exact tool-set match | 0.940 | 50 |
| First tool correct | 0.936 | 47 |
| Argument accuracy | 1 | 43 |
| Argument exact match | 1 | 43 |
| Forbidden-tool violations | 0.020 | 50 |
| Tool errors per case | 0.000 | 50 |
| Retriever Hit@5 | 0.971 | 34 |
| Retriever MRR | 0.848 | 34 |
| Retriever NDCG@10 | 0.754 | 34 |
| Agent searched the knowledge base | 0.912 | 34 |
| Agent namespace choice correct | 1 | 31 |
| Agent Hit@5 | 0.912 | 34 |
| Context precision | 0.724 | 34 |
| Context recall (key facts in contexts) | 0.853 | 34 |
| Answer correctness (key facts in answer) | 0.907 | 34 |
| Cites a retrieved document | 0.936 | 31 |
| Cites the gold document | 0.853 | 34 |
| Unsupported citations per case | 0.000 | 34 |
| Faithfulness (judge) | – | 0 |
| Answer relevancy (judge + embeddings) | – | 0 |
| Answer correctness (judge) | – | 0 |
| Mean latency (ms) | 5883.020 | 50 |
| Mean prompt tokens | 13531.340 | 50 |
| Mean completion tokens | 146.860 | 50 |

## Retrieval at K

Retriever = the raw question against all namespaces (top 10). Agent = the passages Piper's own search_knowledge calls returned, in order.

| Path | K | Recall | Precision | Hit rate | NDCG |
|---|---|---|---|---|---|
| Retriever | 3 | 0.545 | 0.549 | 0.971 | 0.631 |
| Retriever | 5 | 0.706 | 0.453 | 0.971 | 0.685 |
| Retriever | 10 | 0.889 | 0.291 | 1 | 0.754 |
| Agent | 3 | 0.556 | 0.559 | 0.882 | 0.602 |
| Agent | 5 | 0.683 | 0.435 | 0.912 | 0.651 |
| Agent | 10 | 0.693 | 0.221 | 0.912 | 0.648 |

MRR: retriever 0.848, agent 0.777.

## Embeddings (retriever path)

| Metric | Value |
|---|---|
| Relevant passage in top 10 | 1 |
| Rank of first relevant passage (when found) | 1.441 |
| Cosine of first relevant passage | 0.615 |
| Cosine of best non-relevant passage | 0.562 |
| Margin (relevant - best non-relevant) | 0.049 |

## Agent search behaviour

top_k chosen: {5=35, 8=1}  
namespaces chosen: {home_do_it_yourself=5, home_do_it_yourself+power_tools=1, iron_oak=8, iron_oak+machine_operations=5, machine_maintenance=1, machine_maintenance+machine_operations=1, machine_operations=8, power_tools=5, power_tools+home_do_it_yourself=1, power_tools+machine_maintenance+iron_oak=1}  
mean top_k: 5.048, mean searches per RAG case: 0.971

## By category

| Category | Tool pass | Retriever Hit@5 | Retriever MRR | Agent Hit@5 | Context recall | Answer correctness | Faithfulness |
|---|---|---|---|---|---|---|---|
| business_rules | 1 | 0.889 | 0.833 | 0.778 | 0.778 | 0.889 | – |
| home_diy | 1 | 1 | 0.767 | 1 | 0.900 | 0.900 | – |
| machine_maintenance | 1 | 1 | 1 | 1 | 1 | 1 | – |
| machine_models | 1 | 1 | 0.800 | 0.800 | 0.800 | 1 | – |
| machine_operations | 1 | 1 | 0.929 | 1 | 0.857 | 0.810 | – |
| power_tool_safety | 1 | 1 | 0.857 | 1 | 0.929 | 0.952 | – |
| tool_routing | 0.938 | – | – | – | – | – | – |

## Cases

| Case | Tools called | Tool pass | Gold rank (retriever / agent) | Facts in answer |
|---|---|---|---|---|
| rag-mach-01 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-mach-02 | search_knowledge | 1 | 2 / 1 | 1 |
| rag-mach-04 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-mach-10 | search_knowledge | 1 | 2 / 2 | 1 |
| rag-mach-12 | list_machines | 1 | 1 / – | 1 |
| rag-rule-01 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-02 | search_knowledge | 1 | 3 / 3 | 1 |
| rag-rule-03 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-04 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-05 | list_services | 1 | 6 / – | 1 |
| rag-rule-06 | list_services | 1 | 1 / – | 1 |
| rag-rule-07 | search_knowledge, search_knowledge | 1 | 1 / 3 | 0.000 |
| rag-rule-08 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-09 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-01 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-02 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-03 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-04 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-05 | search_knowledge, search_products_by_category | 1 | 1 / 1 | 1 |
| rag-ops-06 | search_knowledge | 1 | 1 / 2 | 1 |
| rag-ops-07 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-08 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-09 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-10 | search_knowledge, search_products_by_category | 1 | 1 / 1 | 1 |
| rag-ops-11 | search_knowledge | 1 | 2 / 2 | 1 |
| rag-diy-03 | search_knowledge | 1 | 2 / 2 | 1 |
| rag-diy-04 | search_knowledge, search_products_by_category | 1 | 3 / 4 | 0.500 |
| rag-diy-05 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-pt-01 | search_knowledge, search_products_by_category | 1 | 1 / 1 | 0.667 |
| rag-pt-02 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-pt-03 | search_knowledge | 1 | 2 / 2 | 1 |
| rag-ops-12 | search_knowledge, search_knowledge | 1 | 1 / 1 | 0.667 |
| rag-ops-13 | search_knowledge | 1 | 1 / 1 | 0.000 |
| rag-ops-14 | search_knowledge | 1 | 2 / 1 | 1 |
| tool-order-01 | get_order | 1 |  |  |
| tool-order-02 | get_order | 1 |  |  |
| tool-stock-01 | search_products | 1 |  |  |
| tool-price-01 | search_products | 1 |  |  |
| tool-mach-01 | list_machines | 1 |  |  |
| tool-mach-02 | search_knowledge | 0.000 |  |  |
| tool-endmill-01 | get_end_mills | 1 |  |  |
| tool-health-01 | diagnose_machine | 1 |  |  |
| tool-health-02 | search_knowledge | 1 |  |  |
| tool-cnc-01 | calculate_cnc_parameters | 1 |  |  |
| tool-booking-01 | list_services, create_service_booking | 1 |  |  |
| tool-booking-02 | list_services | 1 |  |  |
| tool-support-01 | escalate_to_human | 1 |  |  |
| tool-advice-01 | search_knowledge, search_products_by_category | 1 |  |  |
| tool-material-01 | get_material_properties | 1 |  |  |
| tool-offtopic-01 |  | 1 |  |  |

## Failures

Cases with a tool failure, no relevant passage in Piper's contexts, or fewer than all key facts in the answer.

### rag-mach-12

**Q:** What are the spindle speed, table size and price of the VMC-650 machining centre that Iron & Oak sells?  
**Tools:** [list_machines]  
**A:** The VMC-650 has a spindle speed range of **50–8,000 rpm** and a table size of **800 × 400 mm**. Its listed price is **US$28,500**.

### rag-rule-05

**Q:** Can one of your technicians come to my workshop in Cali to service my mill?  
**Tools:** [list_services]  
**A:** Sorry, our technicians only make on-site visits in Bogotá and Medellín, so we can’t send one to Cali. I can help you troubleshoot the mill remotely or check whether another support option is available.

### rag-rule-06

**Q:** What does an Emergency Repair visit cost?  
**Tools:** [list_services]  
**A:** Emergency Repair is **$180 per hour**, with an estimated **2–6 hours** of work. The final cost depends on the time required; parts may be additional.

### rag-rule-07

**Q:** Do the end mills I buy from you come with a warranty?  
**Tools:** [search_knowledge, search_knowledge]  
**A:** End mills are cutters, and our policy excludes cutters and other tooling from Iron & Oak’s warranty. A manufacturer’s warranty or applicable law may still provide coverage, and the product listing or your written quote may specify different terms. The Warranty Team makes the final decision on any claim.  Source: *Iron Oak Company Book*, sections 18.9 and 20.5.

### rag-diy-04

**Q:** How can I check whether the flapper in my toilet tank is leaking?  
**Tools:** [search_knowledge, search_products_by_category]  
**A:** Open the tank lid and add a few drops of food coloring to the tank water—don’t flush. Wait about 30 minutes. If color appears in the bowl, water is leaking past the flapper (flush valve). The test is described in *Home Maintenance For Dummies*, “Toilet Training 101,” p. 232.  If the bowl stays clear, the leak may be elsewhere, such as the fill valve or an external connection.

### rag-pt-01

**Q:** What is kickback on a power tool and how do I prevent it?  
**Tools:** [search_knowledge, search_products_by_category]  
**A:** **Kickback** happens when a blade or bit binds, pinches, or jams in the material and the tool suddenly jerks back toward you. It can happen very quickly and cause serious injury.  To reduce the risk: - Clamp the work securely so it can’t shift or pinch the blade/bit. - Use the correct, undamaged accessory and follow the tool’s manual. - Let the tool reach operating speed before cutting or drilling…

### rag-ops-12

**Q:** What causes chatter and a poor surface finish when milling?  
**Tools:** [search_knowledge, search_knowledge]  
**A:** Chatter and a poor surface finish usually come from vibration or unstable cutting conditions. Common causes include:  - **Too much tool stickout** or a flexible tool/holder. - **Loose or inadequate workholding**, or a workpiece that flexes. - **Worn or damaged cutter**, spindle bearings, or loose machine gibs. - **Unsuitable speeds and feeds**—too aggressive can cause vibration; too slow a feed ca…

### rag-ops-13

**Q:** What is a built-up edge in metal cutting and why does it form?  
**Tools:** [search_knowledge]  
**A:** A **built-up edge (BUE)** is a temporary lump of workpiece metal that sticks to the cutting edge and rake face of a tool. It can act like an extension of the tool, but it continually grows and breaks away.  It forms when metal at the tool–chip interface **seizes or adheres to the tool** under high pressure and severe deformation. Work material accumulates in layers and becomes heavily strain-harde…

### tool-mach-02

**Q:** What's the price of the HB-900 boring mill?  
**Tools:** [search_knowledge] (extra: [search_knowledge], args: [])  
**A:** The HB-900 horizontal boring mill is listed at **$42,000**. The catalog also lists 15 kW power, spindle speeds of 20–1,600 rpm, and a 1,000 × 900 mm table. Source: *Iron Oak Products and Services*.
