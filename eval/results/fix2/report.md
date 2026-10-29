# Piper eval: fix2

Commit `547dd50-dirty`, chat model `gpt-6-luna` (temperature 0.0, seed 42), embeddings `text-embedding-3-small`, retrieval hybrid (dense + BM25, RRF), judge `pending`. 50 cases (34 RAG), started 2026-10-28T21:23:04.053738290-05:00.

## Summary

| Metric | Value | n |
|---|---|---|
| Tool pass (right tools, no forbidden call, exact args) | 0.980 | 50 |
| Tool selection accuracy | 0.980 | 50 |
| Exact tool-set match | 0.920 | 50 |
| First tool correct | 0.936 | 47 |
| Argument accuracy | 1 | 43 |
| Argument exact match | 1 | 43 |
| Forbidden-tool violations | 0.020 | 50 |
| Tool errors per case | 0.000 | 50 |
| Retriever Hit@5 | 0.941 | 34 |
| Retriever MRR | 0.820 | 34 |
| Retriever NDCG@10 | 0.780 | 34 |
| Agent searched the knowledge base | 0.912 | 34 |
| Agent namespace choice correct | 1 | 31 |
| Agent Hit@5 | 0.882 | 34 |
| Context precision | 0.739 | 34 |
| Context recall (key facts in contexts) | 0.882 | 34 |
| Answer correctness (key facts in answer) | 0.927 | 34 |
| Cites a retrieved document | 0.871 | 31 |
| Cites the gold document | 0.794 | 34 |
| Unsupported citations per case | 0.000 | 34 |
| Faithfulness (judge) | – | 0 |
| Answer relevancy (judge + embeddings) | – | 0 |
| Answer correctness (judge) | – | 0 |
| Mean latency (ms) | 5500.540 | 50 |
| Mean prompt tokens | 14281.900 | 50 |
| Mean completion tokens | 146.540 | 50 |

## Retrieval at K

Retriever = the raw question against all namespaces (top 10). Agent = the passages Piper's own search_knowledge calls returned, in order.

| Path | K | Recall | Precision | Hit rate | NDCG |
|---|---|---|---|---|---|
| Retriever | 3 | 0.606 | 0.578 | 0.912 | 0.658 |
| Retriever | 5 | 0.777 | 0.494 | 0.941 | 0.742 |
| Retriever | 10 | 0.889 | 0.297 | 0.971 | 0.780 |
| Agent | 3 | 0.573 | 0.569 | 0.882 | 0.651 |
| Agent | 5 | 0.721 | 0.465 | 0.882 | 0.700 |
| Agent | 10 | 0.731 | 0.235 | 0.882 | 0.702 |

MRR: retriever 0.820, agent 0.775.

## Embeddings (retriever path)

Hybrid run: these scores are RRF fusion scores, not cosines, so compare ranks, not values.

| Metric | Value |
|---|---|
| Relevant passage in top 10 | 0.971 |
| Rank of first relevant passage (when found) | 1.546 |
| Cosine of first relevant passage | 0.032 |
| Cosine of best non-relevant passage | 0.031 |
| Margin (relevant - best non-relevant) | 0.001 |

## Agent search behaviour

top_k chosen: {3=1, 5=36, 8=1}  
namespaces chosen: {home_do_it_yourself=5, home_do_it_yourself+power_tools=1, iron_oak=9, iron_oak+machine_operations=5, machine_maintenance=2, machine_maintenance+machine_operations=1, machine_operations=8, power_tools=7}  
mean top_k: 5.048, mean searches per RAG case: 0.941

## By category

| Category | Tool pass | Retriever Hit@5 | Retriever MRR | Agent Hit@5 | Context recall | Answer correctness | Faithfulness |
|---|---|---|---|---|---|---|---|
| business_rules | 1 | 0.889 | 0.815 | 0.667 | 0.778 | 0.889 | – |
| home_diy | 1 | 0.800 | 0.733 | 1 | 0.900 | 0.900 | – |
| machine_maintenance | 1 | 1 | 0.500 | 1 | 1 | 1 | – |
| machine_models | 1 | 1 | 0.733 | 0.800 | 0.800 | 1 | – |
| machine_operations | 1 | 1 | 1 | 1 | 0.929 | 0.857 | – |
| power_tool_safety | 1 | 1 | 0.814 | 1 | 1 | 1 | – |
| tool_routing | 0.938 | – | – | – | – | – | – |

## Cases

| Case | Tools called | Tool pass | Gold rank (retriever / agent) | Facts in answer |
|---|---|---|---|---|
| rag-mach-01 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-mach-02 | search_knowledge | 1 | 3 / 1 | 1 |
| rag-mach-04 | search_knowledge, search_knowledge | 1 | 1 / 1 | 1 |
| rag-mach-10 | search_knowledge | 1 | 3 / 1 | 1 |
| rag-mach-12 | list_machines | 1 | 1 / – | 1 |
| rag-rule-01 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-02 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-03 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-04 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-05 | list_services | 1 | – / – | 1 |
| rag-rule-06 | list_services | 1 | 1 / – | 1 |
| rag-rule-07 | search_knowledge | 1 | 3 / – | 0.000 |
| rag-rule-08 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-09 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-01 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-02 | search_knowledge | 1 | 5 / 2 | 1 |
| rag-ops-03 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-04 | search_knowledge, search_products_by_category | 1 | 2 / 2 | 1 |
| rag-ops-05 | search_knowledge, search_products_by_category | 1 | 1 / 1 | 1 |
| rag-ops-06 | search_knowledge | 1 | 1 / 2 | 1 |
| rag-ops-07 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-08 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-09 | search_knowledge | 1 | 2 / 2 | 1 |
| rag-ops-10 | search_knowledge, search_products_by_category | 1 | 1 / 1 | 1 |
| rag-ops-11 | search_knowledge | 1 | 2 / 2 | 1 |
| rag-diy-03 | search_knowledge, search_products_by_category | 1 | 1 / 2 | 1 |
| rag-diy-04 | search_knowledge, search_products_by_category | 1 | 6 / 3 | 0.500 |
| rag-diy-05 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-pt-01 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-pt-02 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-pt-03 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-12 | search_knowledge, search_products_by_category | 1 | 1 / 1 | 1 |
| rag-ops-13 | search_knowledge | 1 | 1 / 1 | 0.000 |
| rag-ops-14 | search_knowledge | 1 | 1 / 1 | 1 |
| tool-order-01 | get_order | 1 |  |  |
| tool-order-02 | get_order | 1 |  |  |
| tool-stock-01 | search_products | 1 |  |  |
| tool-price-01 | search_products | 1 |  |  |
| tool-mach-01 | list_machines | 1 |  |  |
| tool-mach-02 | search_knowledge | 0.000 |  |  |
| tool-endmill-01 | get_end_mills | 1 |  |  |
| tool-health-01 | diagnose_machine, search_products_by_category | 1 |  |  |
| tool-health-02 | search_knowledge, search_knowledge | 1 |  |  |
| tool-cnc-01 | calculate_cnc_parameters | 1 |  |  |
| tool-booking-01 | list_services, create_service_booking | 1 |  |  |
| tool-booking-02 | search_knowledge | 1 |  |  |
| tool-support-01 | escalate_to_human | 1 |  |  |
| tool-advice-01 | search_knowledge, search_products_by_category | 1 |  |  |
| tool-material-01 | get_material_properties, search_knowledge | 1 |  |  |
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
**A:** Sorry, our technicians only make on-site visits in Bogotá and Medellín, so we can’t service your mill in Cali. I can help you find a local technician or discuss troubleshooting options remotely.

### rag-rule-06

**Q:** What does an Emergency Repair visit cost?  
**Tools:** [list_services]  
**A:** Emergency Repair is **$180 per hour**, with a **2-hour minimum**. The visit is billed hourly; the final duration and any parts costs depend on the repair.

### rag-rule-07

**Q:** Do the end mills I buy from you come with a warranty?  
**Tools:** [search_knowledge]  
**A:** The general warranty covers defects in materials or workmanship during normal use, but the policy doesn’t specify a warranty period for end mills. It also excludes normal wear and tear, which can apply to cutting tools. The Warranty Team makes the final decision on coverage. *(Iron Oak Company Book, Warranty Policy.)*  For a specific end mill, check its product details or ask Iron & Oak staff to c…

### rag-diy-04

**Q:** How can I check whether the flapper in my toilet tank is leaking?  
**Tools:** [search_knowledge, search_products_by_category]  
**A:** Lift the tank lid and add a few drops of food coloring to the tank water—don’t flush. Wait about 30 minutes. If color appears in the bowl, the flapper/flush valve is leaking and may need cleaning or replacement. Avoid chlorine tank tablets; they can damage the flapper.  Source: *Home Maintenance For Dummies*, “Toilet Training 101,” p. 232.

### rag-ops-13

**Q:** What is a built-up edge in metal cutting and why does it form?  
**Tools:** [search_knowledge]  
**A:** A **built-up edge (BUE)** is a changing lump of workpiece material that sticks to the cutting edge and rake face. It can act like a temporary extension of the tool, then break away in fragments.  It forms when intense pressure and friction at the tool–chip interface cause work material to seize and adhere to the tool. The material is heavily strained and work-hardened as more layers accumulate. BU…

### tool-mach-02

**Q:** What's the price of the HB-900 boring mill?  
**Tools:** [search_knowledge] (extra: [search_knowledge], args: [])  
**A:** The HB-900 horizontal boring mill is listed at **$42,000**. Freight is quoted separately by staff.
