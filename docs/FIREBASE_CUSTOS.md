# Estudo de Custos e Planos do Firebase
**Ideia Space — Repositório Digital Protegido | Squad 22**  
**Referência:** Firebase Pricing (outubro/2026) — firebase.google.com/pricing

---

## 1. Plano Spark (Gratuito)

O plano Spark é adequado para desenvolvimento, testes e MVPs com tráfego baixo.

### Firebase Storage (Cloud Storage for Firebase)

| Recurso | Limite Gratuito |
|---|---|
| Armazenamento total | 5 GB |
| Download (transferência de dados) | 1 GB / dia |
| Operações de upload (escritas) | 20.000 / dia |
| Operações de download (leituras) | 50.000 / dia |
| Operações de listagem | 50.000 / dia |
| Operações de exclusão | Sem limite |

> **Importante:** O limite de 1 GB/dia de download é o gargalo mais relevante para o Ideia Space. Um único vídeo de 500 MB consome metade da quota diária.

### Firebase Authentication

| Recurso | Limite Gratuito |
|---|---|
| Usuários ativos por mês (MAU) | 10.000 |
| Verificações por SMS (Phone Auth) | 10 / dia |
| Login com Google, Email/Senha | Ilimitado |

### Considerações do Plano Spark

- **Adequado para:** ambiente de dev/testes, demos internas, apresentações.
- **Inadequado para:** produção com mais de 10 usuários simultâneos acessando vídeos.
- Signed URLs geradas pelo backend **não contam como operações Firebase** — o acesso vai direto ao Cloud Storage com a assinatura da service account.

---

## 2. Plano Blaze (Pay-as-you-go)

O Plano Blaze inclui as mesmas quotas gratuitas do Spark e cobra apenas o excedente.

### Firebase Storage — Preços (USD, região us-central1)

| Recurso | Custo após a quota gratuita |
|---|---|
| Armazenamento | US$ 0,026 / GB / mês |
| Download (egress) | US$ 0,12 / GB |
| Operações de upload (classe A) | US$ 0,05 / 10.000 operações |
| Operações de leitura/listagem (classe B) | US$ 0,004 / 10.000 operações |

> **Nota sobre regiões:** Os preços acima são para `us-central1`. Regiões no Brasil (`southamerica-east1` — São Paulo) têm egress mais caro: ~US$ 0,15/GB. Para minimizar latência para usuários brasileiros, use a região São Paulo, mas inclua esse custo nas estimativas.

### Firebase Authentication — Plano Blaze

| Recurso | Custo |
|---|---|
| Usuários ativos por mês (MAU) | Gratuito até 10.000; US$ 0,0055 / MAU adicional |
| SMS (Phone Auth) | US$ 0,0060 / SMS (varia por país) |
| Email/Senha, Google OAuth | Gratuito |

---

## 3. Estimativa de Custos por Cenário

### Cenário A — Ambiente de Testes / Dev (Plano Spark)

**Premissas:**
- 5–10 usuários testando simultaneamente
- 20 arquivos: 10 PDFs (~2 MB cada) + 5 vídeos (~100 MB cada) + outros
- Volume de armazenamento: ~600 MB
- Download/dia: ~200 MB (testes manuais)

| Item | Valor |
|---|---|
| Armazenamento (600 MB) | Dentro da quota gratuita (5 GB) |
| Download (~200 MB/dia) | Dentro da quota gratuita (1 GB/dia) |
| Autenticação (<100 MAU) | Gratuito |
| **Total estimado** | **R$ 0,00 / mês** |

✅ **O Plano Spark é suficiente para toda a fase de desenvolvimento e validação do projeto.**

---

### Cenário B — Produção (Escala Futura) — Plano Blaze

**Premissas para uma faculdade de médio porte:**
- 500 alunos ativos / mês
- 30 professores fazendo uploads
- Acervo: 150 PDFs (avg 5 MB) + 50 vídeos (avg 400 MB) = 750 MB + 20 GB = **~21 GB armazenados**
- Taxa de acesso: cada aluno assiste 2 vídeos e abre 5 PDFs/mês
  - Transferência vídeos: 500 alunos × 2 vídeos × 400 MB = **400 GB/mês**
  - Transferência PDFs: 500 alunos × 5 PDFs × 5 MB = **12,5 GB/mês**
  - **Total download: ~412,5 GB/mês**

**Cálculo de custos (região São Paulo):**

| Item | Cálculo | Custo (USD) |
|---|---|---|
| Armazenamento | (21 GB − 5 GB gratuito) × $0,026 | $0,42 |
| Download egress | (412,5 GB − 1 GB/dia×30 gratuito) × $0,15 | ~$57,38 |
| Operações upload (30 prof × 10 uploads) | 300 ops (dentro da quota gratuita) | $0,00 |
| Operações leitura (500 × 7 acessos) | 3.500 ops (dentro da quota gratuita) | $0,00 |
| Firebase Auth (500 MAU) | Gratuito até 10.000 | $0,00 |
| **Total estimado (USD)** | | **~US$ 57,80 / mês** |
| **Total estimado (BRL @ R$5,80)** | | **~R$ 335,24 / mês** |

> **Gargalo:** O egress (download) é o principal custo. Cada GB de vídeo transferido custa R$ 0,87.

**Cenário B+ — 500 GB armazenados, 1.000 alunos:**

| Métrica | Estimativa |
|---|---|
| Armazenamento (500 GB) | ~$12,94/mês |
| Download (estimado 1 TB/mês) | ~$147/mês |
| **Total (USD)** | **~US$ 160/mês** |
| **Total (BRL)** | **~R$ 928/mês** |

---

## 4. Recomendações Técnicas para Otimização de Custos

### 4.1 Expiração curta de Signed URLs

Configure Signed URLs com expiração de 10 a 15 minutos. URLs expiradas não podem ser compartilhadas ou arquivadas por alunos para uso posterior — isso reduz o reaproveitamento não autorizado de URLs que poderiam gerar download extra.

```java
// FirebaseStorageService.java — configurar no application.yml
firebase.storage.signed-url-duration-minutes: 10
```

### 4.2 Compressão e otimização de PDFs

PDFs de slides/apostilas frequentemente têm imagens não comprimidas. Use `ghostscript` ou `ilovepdf` para reduzir o tamanho antes do upload:

```bash
# Exemplo com ghostscript (reduz PDF de 20 MB para ~5 MB)
gs -sDEVICE=pdfwrite -dCompatibilityLevel=1.5 \
   -dPDFSETTINGS=/ebook \
   -dNOPAUSE -dQUIET -dBATCH \
   -sOutputFile=apostila_comprimida.pdf apostila_original.pdf
```

Meta: manter PDFs abaixo de 3 MB e vídeos abaixo de 200 MB por arquivo.

### 4.3 Vídeos: compressão H.264/H.265 + resolução adaptativa

Para videoaulas gravadas em 1080p, comprimir para 720p com H.264 já reduz o tamanho em ~60%:

```bash
ffmpeg -i video_original.mp4 -vcodec h264 -acodec aac \
       -vf scale=1280:720 -crf 28 video_otimizado.mp4
```

Considere geração de múltiplas resoluções (adaptive bitrate com HLS) para redes lentas — isso pode ser implementado com Firebase Hosting + Cloud Functions.

### 4.4 Cache de Signed URLs no backend (TTL = 9 min)

Se o mesmo aluno acessa o mesmo conteúdo várias vezes em uma sessão, cada requisição gera uma nova Signed URL — que custa uma chamada à API do GCS. Um cache em memória (ex.: Spring Cache + Caffeine) com TTL de 9 minutos evita geração repetida:

```java
@Cacheable(value = "signedUrls", key = "#caminhoStorage + ':' + #userId")
public String gerarSignedUrl(String caminhoStorage, Long userId) { ... }
```

### 4.5 Monitoramento de custos no Google Cloud Console

- Ative **Budget Alerts** no Firebase/Google Cloud para receber notificação por email quando os custos atingirem 50%, 80% e 100% do orçamento definido.
- Use o relatório **Firebase Usage and Billing** para identificar os objetos mais acessados e os períodos de maior tráfego.

### 4.6 LGPD: expiração e exclusão de dados

O particionamento mensal de `log_acesso` facilita a remoção de dados antigos conforme a política de retenção da instituição:

```sql
-- Remove logs com mais de 2 anos (exemplo de retenção LGPD)
DROP TABLE IF EXISTS log_acesso_2024_09;
```

Para dados de usuários no Firebase Auth, implemente um endpoint de exclusão de conta que chame `FirebaseAuth.getInstance().deleteUser(uid)` — exigência do direito de apagamento da LGPD (Art. 18, VI).

---

## 5. Recomendação Final

| Fase | Plano Recomendado | Custo Estimado |
|---|---|---|
| Desenvolvimento / Testes / Apresentação | **Spark (Gratuito)** | R$ 0/mês |
| Piloto (até 200 alunos, 1 turma) | **Blaze** | ~R$ 50–100/mês |
| Produção (500+ alunos) | **Blaze** | ~R$ 300–600/mês |
| Escala ampla (múltiplas instituições) | **Blaze + CDN (Cloud CDN)** | Negociar com Google |

**Para a sprint final do projeto:** o **Plano Spark** cobre 100% das necessidades de desenvolvimento e demonstração sem nenhum custo.

**Para o cliente final:** recomendar ativação do **Plano Blaze** apenas quando houver mais de 50 usuários ativos, com alertas de orçamento configurados desde o início.
