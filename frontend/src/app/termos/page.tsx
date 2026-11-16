import type { Metadata } from "next";
import { LegalPage } from "@/components/Legal";
import { VERSAO_TERMOS } from "@/lib/types";

export const metadata: Metadata = { title: "Termos de Uso", description: "Termos de Uso da plataforma Jaguatá.", alternates: { canonical: "/termos" } };

export default function TermosPage() {
  return (
    <LegalPage titulo="Termos de Uso" versao={VERSAO_TERMOS}>
      <section>
        <h2>1. Aceitação</h2>
        <p>
          Ao criar uma conta na Jaguatá, você declara que leu e concorda com estes Termos de Uso e com a Política de
          Privacidade. O aceite fica registrado com a data e a versão dos textos vigentes.
        </p>
      </section>

      <section>
        <h2>2. O que é a plataforma</h2>
        <p>
          A Jaguatá conecta empresas e organizações do terceiro setor (ONGs). A ONG indica e chancela pessoas maiores de
          18 anos para as vagas cadastradas pelas empresas. A plataforma organiza esse encontro; <strong>não é parte do
          contrato de trabalho</strong>, não garante contratação e não substitui a análise da empresa.
        </p>
      </section>

      <section>
        <h2>3. Cadastro e conta</h2>
        <ul>
          <li>O cadastro exige um CNPJ ativo. Razão social, CNAE, porte e endereço são obtidos da Receita Federal.</li>
          <li>É preciso confirmar o e-mail para entrar. O link vale por 24 horas e só pode ser usado uma vez.</li>
          <li>Você é responsável pela confidencialidade da senha e pelas ações feitas com a sua conta.</li>
          <li>A conta de uma ONG só participa de sugestões depois de verificada pela equipe da plataforma.</li>
        </ul>
      </section>

      <section>
        <h2>4. Regras para ONGs</h2>
        <ul>
          <li>Só podem ser cadastradas pessoas com 18 anos completos.</li>
          <li>
            Antes de cadastrar um(a) assistido(a), a ONG deve ter o consentimento dele(a) para o tratamento dos dados,
            conforme o termo abaixo, e <strong>atesta isso no cadastro</strong>. A plataforma registra quem atestou, quando e
            a versão do termo.
          </li>
          <li>As informações cadastradas devem ser verdadeiras e atualizadas.</li>
          <li>
            A ONG pode, a pedido do(a) assistido(a), exportar os dados dele(a) e anonimizá-los a qualquer momento pela área
            de candidatos.
          </li>
        </ul>
      </section>

      <section>
        <h2>5. Regras para empresas</h2>
        <ul>
          <li>As vagas são criadas a partir de um cardápio fechado de cargos.</li>
          <li>Os dados de candidatos recebidos só podem ser usados para avaliar a candidatura à vaga em questão.</li>
          <li>É vedado discriminar candidatos por qualquer critério ilegal.</li>
        </ul>
      </section>

      <section>
        <h2>6. Uso adequado</h2>
        <p>
          É proibido tentar burlar controles de segurança, acessar dados de outras contas, usar a plataforma para envio de
          mensagens não solicitadas ou sobrecarregá-la de forma abusiva. Há limites automáticos de uso que podem bloquear
          temporariamente o acesso.
        </p>
      </section>

      <section>
        <h2>7. Encerramento</h2>
        <p>
          Você pode excluir o seu acesso a qualquer momento em “Sua conta”. A plataforma pode suspender contas que violem
          estes Termos. Os dados cadastrais da empresa ou da ONG (pessoa jurídica) podem permanecer para fins de histórico.
        </p>
      </section>

      <section>
        <h2>8. Alterações</h2>
        <p>
          Estes Termos podem ser atualizados. A versão em vigor aparece no topo desta página; mudanças relevantes serão
          comunicadas por e-mail. [Definir prazo de aviso.]
        </p>
      </section>

      <section>
        <h2>9. Responsabilidade e foro</h2>
        <p>[Definir limitação de responsabilidade, lei aplicável e foro. Texto a cargo do jurídico.]</p>
      </section>

      <section id="consentimento">
        <h2>Termo de consentimento do(a) assistido(a)</h2>
        <p>
          Texto para a ONG ler e coletar do(a) assistido(a) <em>antes</em> de cadastrá-lo(a) na plataforma:
        </p>
        <blockquote className="mt-3 rounded-2xl bg-white/70 p-5">
          <p>
            Eu, assistido(a) pela ONG indicada, autorizo o tratamento dos meus dados pessoais — nome, data de nascimento, CEP,
            preferência de modelo de trabalho e características informadas (como condição de PCD, egresso ou faixa etária) —
            na plataforma Jaguatá, para que a ONG me indique a vagas de empresas e para que as empresas avaliem a minha
            candidatura.
          </p>
          <p className="mt-3">
            Sei que algumas dessas informações são dados pessoais sensíveis, que a empresa verá meu nome e a adequação à vaga,
            e que posso, a qualquer momento, pedir à ONG a cópia dos meus dados, a correção deles ou a sua eliminação, e
            revogar este consentimento sem prejuízo ao atendimento que recebo da ONG.
          </p>
        </blockquote>
      </section>
    </LegalPage>
  );
}
