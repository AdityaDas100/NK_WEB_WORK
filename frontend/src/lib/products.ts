export type DosageForm =
  | "Tablet"
  | "Film-coated tablet"
  | "Capsule"
  | "Injection"
  | "Oral solution"
  | "Inhaler"
  | "Sachet";

export type TherapeuticArea =
  | "Cardiology"
  | "Anti-infectives"
  | "Metabolic care"
  | "Pain & inflammation"
  | "Respiratory"
  | "Gastroenterology";

export interface Product {
  /** Stable identifier, also used as the React key. */
  readonly id: string;
  /** Brand name as printed on the pack. */
  readonly name: string;
  /** International non-proprietary name (INN). */
  readonly genericName: string;
  readonly form: DosageForm;
  readonly strength: string;
  readonly therapeuticArea: TherapeuticArea;
  /** Kept under ~110 characters so the card can clamp it to three lines without losing meaning. */
  readonly indication: string;
  readonly packSize: string;
  /** Prescription-only medicines may not be promoted to the public in most markets. */
  readonly prescriptionOnly: boolean;
  /**
   * Optional packshot path under `/public` (e.g. `/images/products/cardolix-10.jpg`).
   * When absent the card renders a reserved placeholder slot with identical dimensions,
   * so adding images later never moves the layout.
   */
  readonly imageSrc?: string;
  readonly registrations: readonly string[];
  readonly shelfLifeMonths: number;
  readonly storage: string;
}

export const therapeuticAreas: readonly TherapeuticArea[] = [
  "Cardiology",
  "Anti-infectives",
  "Metabolic care",
  "Pain & inflammation",
  "Respiratory",
  "Gastroenterology"
];

export const products: readonly Product[] = [
  {
    id: "cardolix-10",
    name: "Cardolix 10",
    genericName: "Atorvastatin calcium",
    form: "Film-coated tablet",
    strength: "10 mg",
    therapeuticArea: "Cardiology",
    indication: "Adjunct to diet for the management of primary hypercholesterolaemia in adults.",
    packSize: "30 tablets",
    prescriptionOnly: true,
    registrations: ["EU-GMP", "WHO-GMP"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C, protect from moisture"
  },
  {
    id: "cardolix-20",
    name: "Cardolix 20",
    genericName: "Atorvastatin calcium",
    form: "Film-coated tablet",
    strength: "20 mg",
    therapeuticArea: "Cardiology",
    indication: "Lipid-lowering therapy for adults at elevated cardiovascular risk.",
    packSize: "30 tablets",
    prescriptionOnly: true,
    registrations: ["EU-GMP", "WHO-GMP"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C, protect from moisture"
  },
  {
    id: "tenoril-5",
    name: "Tenoril 5",
    genericName: "Amlodipine besilate",
    form: "Tablet",
    strength: "5 mg",
    therapeuticArea: "Cardiology",
    indication: "Treatment of hypertension and chronic stable angina pectoris.",
    packSize: "30 tablets",
    prescriptionOnly: true,
    registrations: ["WHO-GMP", "ISO 9001"],
    shelfLifeMonths: 36,
    storage: "Below 30 °C, protect from light"
  },
  {
    id: "pressotab-50",
    name: "Pressotab 50",
    genericName: "Losartan potassium",
    form: "Film-coated tablet",
    strength: "50 mg",
    therapeuticArea: "Cardiology",
    indication: "Management of essential hypertension in adults and paediatric patients.",
    packSize: "28 tablets",
    prescriptionOnly: true,
    registrations: ["EU-GMP", "WHO-GMP"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C"
  },
  {
    id: "azimed-500",
    name: "Azimed 500",
    genericName: "Azithromycin dihydrate",
    form: "Film-coated tablet",
    strength: "500 mg",
    therapeuticArea: "Anti-infectives",
    indication: "Short-course therapy for susceptible respiratory and skin infections.",
    packSize: "3 tablets",
    prescriptionOnly: true,
    registrations: ["EU-GMP", "WHO-GMP"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C"
  },
  {
    id: "ceftrix-1g",
    name: "Ceftrix 1 g",
    genericName: "Ceftriaxone sodium",
    form: "Injection",
    strength: "1 g powder for solution",
    therapeuticArea: "Anti-infectives",
    indication: "Parenteral treatment of moderate to severe bacterial infections.",
    packSize: "1 vial",
    prescriptionOnly: true,
    registrations: ["EU-GMP", "FDA registered"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C, protect from light"
  },
  {
    id: "amoxil-k-625",
    name: "Amoxil-K 625",
    genericName: "Amoxicillin + clavulanic acid",
    form: "Film-coated tablet",
    strength: "500 mg + 125 mg",
    therapeuticArea: "Anti-infectives",
    indication: "Broad-spectrum therapy for common upper and lower respiratory tract infections.",
    packSize: "21 tablets",
    prescriptionOnly: true,
    registrations: ["WHO-GMP", "ISO 9001"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C, protect from moisture"
  },
  {
    id: "glucofine-500",
    name: "Glucofine 500",
    genericName: "Metformin hydrochloride",
    form: "Film-coated tablet",
    strength: "500 mg",
    therapeuticArea: "Metabolic care",
    indication: "First-line oral therapy for type 2 diabetes mellitus as an adjunct to diet.",
    packSize: "30 tablets",
    prescriptionOnly: true,
    registrations: ["EU-GMP", "WHO-GMP"],
    shelfLifeMonths: 36,
    storage: "Below 30 °C"
  },
  {
    id: "glyzenta-100",
    name: "Glyzenta 100",
    genericName: "Sitagliptin phosphate",
    form: "Film-coated tablet",
    strength: "100 mg",
    therapeuticArea: "Metabolic care",
    indication: "Once-daily therapy to improve glycaemic control in adults with type 2 diabetes.",
    packSize: "28 tablets",
    prescriptionOnly: true,
    registrations: ["EU-GMP"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C"
  },
  {
    id: "thyronex-50",
    name: "Thyronex 50",
    genericName: "Levothyroxine sodium",
    form: "Tablet",
    strength: "50 µg",
    therapeuticArea: "Metabolic care",
    indication: "Replacement therapy in hypothyroidism and TSH suppression in thyroid disease.",
    packSize: "100 tablets",
    prescriptionOnly: true,
    registrations: ["EU-GMP", "WHO-GMP"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C, protect from light"
  },
  {
    id: "ibuprox-400",
    name: "Ibuprox 400",
    genericName: "Ibuprofen",
    form: "Film-coated tablet",
    strength: "400 mg",
    therapeuticArea: "Pain & inflammation",
    indication: "Short-term relief of mild to moderate pain and inflammation.",
    packSize: "20 tablets",
    prescriptionOnly: false,
    registrations: ["WHO-GMP", "ISO 9001"],
    shelfLifeMonths: 36,
    storage: "Below 30 °C"
  },
  {
    id: "diclofen-sr-100",
    name: "Diclofen SR 100",
    genericName: "Diclofenac sodium (prolonged release)",
    form: "Tablet",
    strength: "100 mg",
    therapeuticArea: "Pain & inflammation",
    indication: "Once-daily management of chronic musculoskeletal and arthritic pain.",
    packSize: "30 tablets",
    prescriptionOnly: true,
    registrations: ["EU-GMP", "WHO-GMP"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C, protect from moisture"
  },
  {
    id: "bronchovent-100",
    name: "Bronchovent 100",
    genericName: "Salbutamol sulfate",
    form: "Inhaler",
    strength: "100 µg per metered dose",
    therapeuticArea: "Respiratory",
    indication: "Relief of acute bronchospasm in asthma and reversible airway obstruction.",
    packSize: "1 × 200 doses",
    prescriptionOnly: true,
    registrations: ["EU-GMP", "WHO-GMP"],
    shelfLifeMonths: 24,
    storage: "Below 30 °C, pressurised container"
  },
  {
    id: "montekulast-10",
    name: "Montekulast 10",
    genericName: "Montelukast sodium",
    form: "Film-coated tablet",
    strength: "10 mg",
    therapeuticArea: "Respiratory",
    indication: "Add-on prophylaxis of asthma and relief of seasonal allergic rhinitis.",
    packSize: "28 tablets",
    prescriptionOnly: true,
    registrations: ["EU-GMP"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C, protect from light"
  },
  {
    id: "pantogard-40",
    name: "Pantogard 40",
    genericName: "Pantoprazole sodium sesquihydrate",
    form: "Film-coated tablet",
    strength: "40 mg",
    therapeuticArea: "Gastroenterology",
    indication: "Management of acid-related disorders including reflux oesophagitis.",
    packSize: "28 tablets",
    prescriptionOnly: true,
    registrations: ["EU-GMP", "WHO-GMP"],
    shelfLifeMonths: 36,
    storage: "Below 25 °C"
  },
  {
    id: "ondaset-4",
    name: "Ondaset 4",
    genericName: "Ondansetron hydrochloride dihydrate",
    form: "Oral solution",
    strength: "4 mg / 5 mL",
    therapeuticArea: "Gastroenterology",
    indication: "Prevention of nausea and vomiting, including post-operative and chemotherapy induced.",
    packSize: "100 mL bottle",
    prescriptionOnly: true,
    registrations: ["WHO-GMP", "ISO 9001"],
    shelfLifeMonths: 24,
    storage: "Below 25 °C, do not freeze"
  }
];
