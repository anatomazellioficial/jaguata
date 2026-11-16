import type { Metadata } from "next";
import { LegalPage } from "@/components/Legal";
import { VERSAO_TERMOS } from "@/lib/types";

export const metadata: Metadata = { title: "Política de Privacidade — Jaguatá" };

export default function PrivacidadePage() {
  return (
    <LegalPage titulo="Política de Privacidade" versao={VERSAO_TERMOS}>
      <section>
        <h2>1. Quem somos</h2>
        <p>
          O controlador dos dados é [razão social e CNPJ da entidade responsável pela Jaguatá]. O contato do encarregado
          pelo tratamento de dados (DPO) é [e-mail do encarregado].
        </p>
      </section>

      <section>
        <h2>2. Quais dados tratamos</h2>
        <ul>
          <li><strong>Conta:</strong> e-mail, senha (guardada apenas como hash), papel, versão e data do aceite dos Termos.</li>
          <li><strong>Empresa e ONG (pessoa jurídica):</strong> CNPJ, razão social, CNAE, porte, endereço da sede e coordenadas, obtidos de fontes públicas.</li>
          <li>
            <strong>Candidatos (cadastrados pelas ONGs):</strong> nome, data de nascimento, CEP e coordenadas, preferência
            de trabalho e tags, que podem revelar <em>dados sensíveis</em> (por exemplo, condição de PCD ou situação de
            vulnerabilidade).
          </li>
          <li><strong>Sessão:</strong> identificador da sessão, navegador (user-agent) e datas de criação e expiração.</li>
          <li><strong>Auditoria:</strong> quem realizou ações sensíveis (cadastro, exportação, anonimização, verificação) e quando, sem nomes nem e-mails.</li>
        </ul>
      </section>

      <section>
        <h2>3. Para que usamos e em que base legal</h2>
        <ul>
          <li>Executar o serviço de conexão entre ONGs e empresas (execução de contrato).</li>
          <li>Tratar dados sensíveis de candidatos com o <strong>consentimento</strong> do titular, coletado pela ONG e atestado no cadastro.</li>
          <li>Proteger a plataforma contra fraude e abuso: limites de uso, bloqueio temporário de contas e trilha de auditoria (legítimo interesse).</li>
          <li>Enviar e-mails transacionais (confirmação de conta, recuperação de senha, avisos de segurança).</li>
        </ul>
      </section>

      <section>
        <h2>4. Com quem compartilhamos</h2>
        <ul>
          <li><strong>Empresas</strong> veem o nome do candidato, a adequação à vaga (score) e o andamento, somente nas vagas em que ele foi indicado.</li>
          <li><strong>ONGs</strong> veem apenas os candidatos que cadastraram e o título (já traduzido) da vaga.</li>
          <li>
            <strong>Serviços de consulta:</strong> o CNPJ e o CEP informados são enviados à BrasilAPI e, para obter coordenadas,
            ao OpenStreetMap/Nominatim. Nenhum nome, e-mail ou dado de candidato é enviado.
          </li>
          <li><strong>E-mail:</strong> o provedor de envio recebe o e-mail do destinatário e o texto da mensagem. [Informar o provedor.]</li>
          <li>[Informar hospedagem/provedor de nuvem e país dos servidores.]</li>
        </ul>
      </section>

      <section>
        <h2>5. Por quanto tempo guardamos</h2>
        <ul>
          <li>Conta: enquanto estiver ativa; ao excluir o acesso, login, sessões e links pendentes são apagados.</li>
          <li>Sessões: até 30 dias; links de e-mail: 24 horas (verificação) ou 30 minutos (senha); registros expirados são removidos periodicamente.</li>
          <li>Candidato: até a ONG removê-lo ou o titular pedir a eliminação (anonimização imediata).</li>
          <li>Auditoria: [definir prazo de retenção].</li>
        </ul>
      </section>

      <section>
        <h2>6. Seus direitos (LGPD, art. 18)</h2>
        <p>Você pode pedir confirmação do tratamento, acesso, correção, anonimização ou eliminação, portabilidade e revogação do consentimento.</p>
        <ul>
          <li><strong>Usuários:</strong> em “Sua conta” você baixa uma cópia dos dados da conta e pode excluir o seu acesso.</li>
          <li><strong>Candidatos:</strong> peça à ONG que o(a) atende. Ela pode exportar todos os seus dados e anonimizá-los na hora, e cada ação fica auditada.</li>
          <li>Em qualquer caso, também é possível falar com o encarregado: [e-mail do encarregado].</li>
        </ul>
      </section>

      <section>
        <h2>7. Segurança</h2>
        <p>
          Senhas com hash BCrypt; sessões com tokens de curta duração e renovação rotativa; links de uso único; limites de
          tentativa de acesso; cabeçalhos de segurança; trilha de auditoria; acesso restrito por papel e por posse do recurso.
          Nenhum sistema é totalmente imune; em caso de incidente relevante, comunicaremos os titulares e a ANPD conforme a lei.
        </p>
      </section>

      <section>
        <h2>8. Cookies</h2>
        <p>
          Usamos apenas cookies essenciais de sessão (httpOnly), necessários para você permanecer conectado. Não usamos cookies
          de publicidade ou de rastreamento.
        </p>
      </section>

      <section>
        <h2>9. Alterações</h2>
        <p>Esta política pode ser atualizada; a versão vigente aparece no topo. Mudanças relevantes serão comunicadas por e-mail.</p>
      </section>
    </LegalPage>
  );
}
