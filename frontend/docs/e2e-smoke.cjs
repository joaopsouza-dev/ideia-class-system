const {chromium}=require('playwright');
const B='http://localhost:4173', SH=process.argv[2]+'/shots/';
const results=[];const ok=(n,c,d='')=>{results.push([c?'PASS':'FAIL',n,d]);console.log((c?'PASS ':'FAIL ')+n+(d?' — '+d:''))};
(async()=>{
const browser=await chromium.launch();
const ctx=await browser.newContext({viewport:{width:1280,height:800}});
const page=await ctx.newPage();
const errs=[];page.on('pageerror',e=>errs.push('pageerror '+e.message));page.on('console',m=>{if(m.type()==='error')errs.push('console '+m.text())});
const login=async(email)=>{await page.goto(B+'/login');await page.fill('input[type=email]',email);await page.fill('input[type=password]','Senha@123');await page.click('button[type=submit]');};
// redirect when unauthenticated
await page.goto(B+'/biblioteca');await page.waitForURL('**/login');ok('rota protegida redireciona ao login',true);
// validation
await page.click('button[type=submit]');ok('validação de campos vazios',(await page.locator('[role=alert]').count())>=2);
await page.fill('input[type=email]','x@y.com');await page.fill('input[type=password]','errada123');await page.click('button[type=submit]');
await page.waitForSelector('text=E-mail ou senha incorretos');ok('credenciais inválidas',true);
await page.fill('input[type=email]','joao.pedro@ideiaspace.dev');await page.fill('input[type=password]','Senha@123');await page.click('button[type=submit]');
await page.waitForSelector('text=conta está inativa');ok('conta inativa bloqueada',true);
await page.screenshot({path:SH+'01-login.png'});
// teacher
await login('victor.emanoel@ideiaspace.dev');await page.waitForSelector('text=Olá, Prof. Victor');ok('login professor',true);
await page.waitForSelector('text=Materiais recentes');await page.waitForTimeout(600);
await page.screenshot({path:SH+'02-teacher-dash.png',fullPage:true});
await page.click('nav >> text=Biblioteca');await page.waitForSelector('text=Lista de exercícios 01');ok('biblioteca do professor inclui rascunho',true);
await page.screenshot({path:SH+'03-teacher-library.png',fullPage:true});
// publish draft
await page.click('button[aria-label="Publicar Lista de exercícios 01 — Órbitas"]');await page.waitForSelector('text=Material publicado');ok('publicar rascunho',true);
// delete with confirm
await page.click('button[aria-label^="Excluir Lista de exercícios"]');await page.waitForSelector('text=Excluir material?');
await page.click('[role=dialog] >> text=Cancelar');ok('cancelar exclusão mantém item',await page.locator('text=Lista de exercícios 01').count()>0);
// upload
await page.click('nav >> text=Novo material');await page.waitForSelector('h1:has-text("Novo material")');
await page.click('button[type=submit]');ok('upload valida campos',(await page.locator('[role=alert]').count())>=2);
await page.fill('input[maxlength="150"]','Apostila enviada no teste');
await page.selectOption('select >> nth=1','3');
await page.setInputFiles('input[type=file]','public/demo/rls.pdf');
await page.fill('textarea','Arquivo enviado pelo teste automatizado.');
await page.screenshot({path:SH+'04-editor.png',fullPage:true});
await page.click('button[type=submit]');await page.waitForSelector('text=Enviando');ok('estado de upload com progresso',true);
await page.waitForURL('**/biblioteca');await page.waitForSelector('text=Apostila enviada no teste');ok('upload concluído e listado',true);
// open uploaded pdf
await page.click('a:has-text("Apostila enviada no teste")');
await page.waitForSelector('canvas');await page.waitForTimeout(1200);
ok('PDF renderizado em canvas',(await page.locator('canvas').count())===1);
ok('marca d\'água presente',(await page.locator('[data-watermark=true]').count())===1);
ok('sem botão de download',(await page.locator('text=/baixar|download/i').filter({hasText:/^(baixar|download)$/i}).count())===0&&(await page.locator('a[download]').count())===0);
await page.screenshot({path:SH+'05-pdf-viewer.png'});
await page.click('button[aria-label="Próxima página"]');await page.waitForTimeout(500);
ok('navegação de página',(await page.inputValue('input[type=number]'))==='2');
await page.click('button[aria-label="Aumentar zoom"]');ok('zoom',(await page.locator('text=125%').count())>0);
// protection
await page.keyboard.press('Control+p');await page.waitForSelector('text=Ação não permitida');ok('Ctrl+P bloqueado e avisado',true);
await page.keyboard.press('PrintScreen');await page.waitForTimeout(300);
// remove watermark -> restored
await page.evaluate(()=>document.querySelector('[data-watermark=true]').remove());await page.waitForTimeout(500);
ok('marca d\'água restaurada após remoção',(await page.locator('[data-watermark=true]').count())===1);
// logout
await page.click('button[aria-label="Sair"]');await page.waitForURL('**/login');ok('logout',true);
// student
await login('maria.clara@ideiaspace.dev');await page.waitForSelector('text=Olá, Maria');ok('login aluno',true);
await page.waitForTimeout(700);await page.screenshot({path:SH+'06-student-dash.png',fullPage:true});
await page.click('nav >> text=Biblioteca');await page.waitForSelector('text=Apostila: Leis de Kepler');
const txt=await page.locator('main').innerText();
ok('aluno não vê rascunhos',!txt.includes('Revisão de modelagem')&&!txt.includes('Rascunho'));
ok('aluno não vê turma que não cursa (BD)',!txt.includes('Segurança por linha'));
// (upload em memória some a cada recarga completa — esperado no modo demonstração)
await page.fill('input[type=search]','kepler');await page.waitForTimeout(500);
ok('busca filtra',(await page.locator('main h3').count())===1);
await page.screenshot({path:SH+'07-student-library.png'});
await page.fill('input[type=search]','zzzz');await page.waitForSelector('text=Nenhum resultado');ok('estado vazio de busca',true);
await page.click('text=Limpar filtros');
await page.selectOption('select[aria-label=Tipo]','VIDEO');await page.waitForTimeout(300);
ok('filtro por tipo',(await page.locator('main h3').count())===2);
// video
await page.click('a:has-text("Órbitas no Sistema Solar")');await page.waitForSelector('video');await page.waitForTimeout(1200);
await page.click('button[aria-label="Reproduzir"]');await page.waitForTimeout(2500);
const t=await page.evaluate(()=>document.querySelector('video').currentTime);ok('vídeo reproduz',t>0.5,'t='+t.toFixed(1));
ok('vídeo sem controles nativos / download',await page.evaluate(()=>{const v=document.querySelector('video');return !v.controls&&v.controlsList.contains('nodownload')}));
await page.screenshot({path:SH+'08-video.png'});
await page.keyboard.press('Control+s');await page.waitForSelector('text=O download deste conteúdo não é permitido');ok('Ctrl+S bloqueado no vídeo',true);
// forbidden
await page.goto(B+'/usuarios');await page.waitForSelector('text=Acesso não autorizado');ok('aluno bloqueado em /usuarios',true);
await page.goto(B+'/material/inexistente');await page.waitForSelector('text=Material não encontrado');ok('material inexistente → erro',true);
// admin
await page.goto(B+'/');await page.click('button[aria-label="Sair"]');await page.waitForURL('**/login');
await login('admin@ideiaspace.dev');await page.waitForSelector('text=Painel administrativo');
await page.click('nav >> text=Usuários');await page.waitForSelector('text=Lucas Mendes Rocha');ok('admin lista usuários',true);
await page.screenshot({path:SH+'09-admin-users.png',fullPage:true});
await page.click('button:has-text("Desativar") >> nth=2');await page.waitForSelector('text=Desativar usuário?');await page.click('[role=dialog] button:has-text("Desativar")');await page.waitForSelector('text=Usuário desativado');ok('desativar usuário',true);
await page.click('nav >> text=Auditoria');await page.waitForSelector('table');
ok('auditoria registra tentativas do aluno',(await page.locator('text=Tentativa de captura').count())>0);
const [dl]=await Promise.all([page.waitForEvent('download'),page.click('text=Exportar CSV')]);ok('exportar CSV',dl.suggestedFilename().endsWith('.csv'));
await page.screenshot({path:SH+'10-audit.png',fullPage:true});
// mobile
const m=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:2});const mp=await m.newPage();
await mp.goto(B+'/login');await mp.screenshot({path:SH+'11-mobile-login.png'});
await mp.fill('input[type=email]','maria.clara@ideiaspace.dev');await mp.fill('input[type=password]','Senha@123');await mp.click('button[type=submit]');await mp.waitForSelector('text=Olá, Maria');await mp.waitForTimeout(600);
await mp.screenshot({path:SH+'12-mobile-dash.png'});
await mp.click('button[aria-label="Abrir menu"]');await mp.waitForTimeout(300);await mp.screenshot({path:SH+'13-mobile-menu.png'});
await mp.click('nav:visible >> text=Biblioteca');await mp.waitForSelector('text=Apostila: Leis de Kepler');
const overflow=await mp.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth);ok('sem scroll horizontal no mobile',!overflow);
await mp.click('a:has-text("Leis de Kepler")');await mp.waitForSelector('canvas');await mp.waitForTimeout(1200);await mp.screenshot({path:SH+'14-mobile-pdf.png'});
ok('sem erros JS no console',errs.length===0,errs.slice(0,3).join(' | '));
console.log('\nTOTAL',results.filter(r=>r[0]==='PASS').length,'/',results.length);
await browser.close();})().catch(e=>{console.error('CRASH',e.message.split('\n')[0]);process.exit(1)});
