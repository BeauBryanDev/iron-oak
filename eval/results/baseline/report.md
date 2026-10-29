# Piper eval: baseline

Commit `0b340bd-dirty`, chat model `gpt-6-luna` (temperature 0.0, seed 42), embeddings `text-embedding-3-small`, retrieval dense, judge `claude-haiku-5-5`. 50 cases (34 RAG), started 2026-10-28T11:58:16.641409918-05:00.

## Summary

| Metric | Value | n |
|---|---|---|
| Tool pass (right tools, no forbidden call, exact args) | 0.960 | 50 |
| Tool selection accuracy | 0.960 | 50 |
| Exact tool-set match | 0.880 | 50 |
| First tool correct | 0.872 | 47 |
| Argument accuracy | 1 | 42 |
| Argument exact match | 1 | 42 |
| Forbidden-tool violations | 0.000 | 50 |
| Tool errors per case | 0.040 | 50 |
| Retriever Hit@5 | 0.971 | 34 |
| Retriever MRR | 0.848 | 34 |
| Retriever NDCG@10 | 0.754 | 34 |
| Agent searched the knowledge base | 0.853 | 34 |
| Agent namespace choice correct | 1 | 29 |
| Agent Hit@5 | 0.853 | 34 |
| Context precision | 0.684 | 34 |
| Context recall (key facts in contexts) | 0.765 | 34 |
| Answer correctness (key facts in answer) | 0.809 | 34 |
| Cites a retrieved document | 0.828 | 29 |
| Cites the gold document | 0.706 | 34 |
| Unsupported citations per case | 0.000 | 34 |
| Faithfulness (judge) | 0.796 | 34 |
| Answer relevancy (judge + embeddings) | 0.786 | 34 |
| Answer correctness (judge) | 0.853 | 34 |
| Mean latency (ms) | 4935.900 | 50 |
| Mean prompt tokens | 15925.440 | 50 |
| Mean completion tokens | 136.540 | 50 |

## Retrieval at K

Retriever = the raw question against all namespaces (top 10). Agent = the passages Piper's own search_knowledge calls returned, in order.

| Path | K | Recall | Precision | Hit rate | NDCG |
|---|---|---|---|---|---|
| Retriever | 3 | 0.545 | 0.549 | 0.971 | 0.631 |
| Retriever | 5 | 0.706 | 0.453 | 0.971 | 0.685 |
| Retriever | 10 | 0.889 | 0.291 | 1 | 0.754 |
| Agent | 3 | 0.504 | 0.510 | 0.824 | 0.565 |
| Agent | 5 | 0.607 | 0.382 | 0.853 | 0.597 |
| Agent | 10 | 0.607 | 0.191 | 0.853 | 0.592 |

MRR: retriever 0.848, agent 0.723.

## Embeddings (retriever path)

| Metric | Value |
|---|---|
| Relevant passage in top 10 | 1 |
| Rank of first relevant passage (when found) | 1.441 |
| Cosine of first relevant passage | 0.615 |
| Cosine of best non-relevant passage | 0.562 |
| Margin (relevant - best non-relevant) | 0.049 |

## Agent search behaviour

top_k chosen: {3=2, 5=30}  
namespaces chosen: {home_do_it_yourself=5, home_do_it_yourself+power_tools=1, iron_oak=7, iron_oak+machine_operations=1, machine_maintenance=1, machine_maintenance+machine_operations=1, machine_operations=7, machine_operations+iron_oak=1, machine_operations+machine_maintenance=1, power_tools=7}  
mean top_k: 4.862, mean searches per RAG case: 0.853

## By category

| Category | Tool pass | Retriever Hit@5 | Retriever MRR | Agent Hit@5 | Context recall | Answer correctness | Faithfulness |
|---|---|---|---|---|---|---|---|
| business_rules | 1 | 0.889 | 0.833 | 0.778 | 0.667 | 0.778 | 0.857 |
| home_diy | 1 | 1 | 0.767 | 1 | 0.900 | 0.900 | 0.629 |
| machine_maintenance | 1 | 1 | 1 | 1 | 1 | 1 | 0.875 |
| machine_models | 0.600 | 1 | 0.800 | 0.400 | 0.400 | 0.600 | 0.767 |
| machine_operations | 1 | 1 | 0.929 | 1 | 0.857 | 0.857 | 0.853 |
| power_tool_safety | 1 | 1 | 0.857 | 1 | 0.929 | 0.857 | 0.790 |
| tool_routing | 1 | – | – | – | – | – | – |

## Cases

| Case | Tools called | Tool pass | Gold rank (retriever / agent) | Facts in answer |
|---|---|---|---|---|
| rag-mach-01 | list_machines | 0.000 | 1 / – | 0.000 |
| rag-mach-02 | list_machines, search_knowledge | 1 | 2 / 1 | 1 |
| rag-mach-04 | list_machines, list_machines | 0.000 | 1 / – | 0.000 |
| rag-mach-10 | list_machines, search_knowledge | 1 | 2 / 2 | 1 |
| rag-mach-12 | list_machines | 1 | 1 / – | 1 |
| rag-rule-01 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-02 | search_knowledge | 1 | 3 / 3 | 0.000 |
| rag-rule-03 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-04 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-rule-05 | list_services | 1 | 6 / – | 1 |
| rag-rule-06 | list_services | 1 | 1 / – | 1 |
| rag-rule-07 | search_knowledge | 1 | 1 / 2 | 0.000 |
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
| rag-ops-10 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-ops-11 | search_knowledge | 1 | 2 / 2 | 0.000 |
| rag-diy-03 | search_knowledge, search_products_by_category | 1 | 2 / 2 | 1 |
| rag-diy-04 | search_knowledge, search_products_by_category | 1 | 3 / 4 | 0.500 |
| rag-diy-05 | search_knowledge, search_products_by_category | 1 | 1 / 1 | 1 |
| rag-pt-01 | search_knowledge, search_products_by_category | 1 | 1 / 1 | 1 |
| rag-pt-02 | search_knowledge | 1 | 1 / 1 | 1 |
| rag-pt-03 | search_knowledge | 1 | 2 / 2 | 1 |
| rag-ops-12 | search_knowledge, search_products_by_category | 1 | 1 / 1 | 1 |
| rag-ops-13 | search_knowledge | 1 | 1 / 1 | 0.000 |
| rag-ops-14 | search_knowledge | 1 | 2 / 1 | 1 |
| tool-order-01 | get_order | 1 |  |  |
| tool-order-02 | get_order | 1 |  |  |
| tool-stock-01 | search_products | 1 |  |  |
| tool-price-01 | search_products | 1 |  |  |
| tool-mach-01 | list_machines | 1 |  |  |
| tool-mach-02 | list_machines | 1 |  |  |
| tool-endmill-01 | get_end_mills | 1 |  |  |
| tool-health-01 | diagnose_machine | 1 |  |  |
| tool-health-02 | search_knowledge | 1 |  |  |
| tool-cnc-01 | calculate_cnc_parameters | 1 |  |  |
| tool-booking-01 | list_services, create_service_booking | 1 |  |  |
| tool-booking-02 | list_services | 1 |  |  |
| tool-support-01 | escalate_to_human | 1 |  |  |
| tool-advice-01 | search_knowledge, search_products_by_category | 1 |  |  |
| tool-material-01 | get_material_properties, search_knowledge | 1 |  |  |
| tool-offtopic-01 |  | 1 |  |  |

## Failures

Cases with a tool failure, no relevant passage in Piper's contexts, or fewer than all key facts in the answer.

### rag-mach-01

**Q:** What is the maximum spindle speed of the AVIA VMC 650 HS V machining centre?  
**Tools:** [list_machines] (extra: [list_machines], args: [])  
**A:** The catalog lists the VMC-650’s maximum spindle speed as **8,000 RPM**. It doesn’t specify the AVIA VMC 650 HS V variant separately, so please confirm the exact model with Iron & Oak staff if you need its manufacturer-specific specification.

### rag-mach-04

**Q:** How much do the AVIA VMC 650 and the AVIA VMC 1300 weigh?  
**Tools:** [list_machines, list_machines] (extra: [list_machines], args: [])  
**A:** I couldn’t find either model in our current machine catalog, so I can’t verify their weights. Please check with Iron & Oak staff or share a link or exact model code, and I’ll help look into it.

### rag-mach-12

**Q:** What are the spindle speed, table size and price of the VMC-650 machining centre that Iron & Oak sells?  
**Tools:** [list_machines]  
**A:** The VMC-650 has a spindle speed range of **50–8,000 RPM**, a table size of **800 × 400 mm**, and a listed price of **$28,500 USD**.

### rag-rule-02

**Q:** How much does shipping to Costa Rica cost?  
**Tools:** [search_knowledge]  
**A:** Costa Rica is served by air, but I don’t have the fixed shipping price available here. The exact cost will appear at checkout once you add your items and delivery address.

### rag-rule-05

**Q:** Can one of your technicians come to my workshop in Cali to service my mill?  
**Tools:** [list_services]  
**A:** Sorry, our technicians only make on-site visits in Bogotá and Medellín, so we can’t send one to Cali. I can help you find a local service option or troubleshoot the mill remotely.

### rag-rule-06

**Q:** What does an Emergency Repair visit cost?  
**Tools:** [list_services]  
**A:** Emergency Repair is **$180 per hour**, with a **2–6 hour estimated visit**. That’s an estimated **$360–$1,080** for the visit; the final charge depends on the actual hours worked.

### rag-rule-07

**Q:** Do the end mills I buy from you come with a warranty?  
**Tools:** [search_knowledge]  
**A:** End mills are considered tooling/cutters and aren’t covered by Iron & Oak’s standard warranty. The general warranty covers defects in materials or workmanship during the applicable warranty period, but the policy specifically excludes cutters. A manufacturer’s warranty or applicable law may provide additional rights. (Iron Oak Company Book, Warranty Policy §§18.2, 18.9)

### rag-ops-11

**Q:** My electrical test needs a CAT II rated meter. Can I use a meter rated CAT III or CAT IV instead?  
**Tools:** [search_knowledge]  
**A:** Yes. A properly rated CAT III or CAT IV meter can be used for a CAT II test, provided its **voltage rating is also at least as high as the test requires**. Check that the meter and test leads are correctly rated, undamaged, and used within their marked limits. De-energize the circuit when possible and follow appropriate safety procedures.  According to *Electrical Measurement Equipments* (pp. 6–7)…

### rag-diy-04

**Q:** How can I check whether the flapper in my toilet tank is leaking?  
**Tools:** [search_knowledge, search_products_by_category]  
**A:** Lift the tank lid and add a few drops of food coloring to the tank water—don’t flush. Wait about 30 minutes, then check the toilet bowl. If colored water appears in the bowl, the flapper or flush valve is leaking and may need replacement. This dye test is described in *Home Maintenance For Dummies*, page 232.

### rag-ops-13

**Q:** What is a built-up edge in metal cutting and why does it form?  
**Tools:** [search_knowledge]  
**A:** A **built-up edge (BUE)** is a layer of workpiece metal that sticks to and accumulates around the cutting edge and rake face. It can act like a temporary extension of the tool, changing its cutting geometry.  It forms when metal at the tool–chip interface **seizes or adheres to the tool** under high pressure and severe deformation. The stuck material becomes heavily strain-hardened; layers build u…
