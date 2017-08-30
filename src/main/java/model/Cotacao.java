package model;

public class Cotacao {
	private String cia;
	private String dtIda;
	private String dtVolta;
	private Float valor;
	private String dataHora;

	public Cotacao() {
	}

	public Cotacao(String cia, String dtIda, String dtVolta, Float valor, String dataHora) {
		this.cia = cia;
		this.dtIda = dtIda;
		this.dtVolta = dtVolta;
		this.valor = valor;
		this.dataHora = dataHora;
	}

	public String getCia() {
		return cia;
	}

	public void setCia(String cia) {
		this.cia = cia;
	}

	public String getDtIda() {
		return dtIda;
	}

	public void setDtIda(String dtIda) {
		this.dtIda = dtIda;
	}

	public String getDtVolta() {
		return dtVolta;
	}

	public void setDtVolta(String dtVolta) {
		this.dtVolta = dtVolta;
	}

	public Float getValor() {
		return valor;
	}

	public void setValor(Float valor) {
		this.valor = valor;
	}

	public String getDataHora() {
		return dataHora;
	}

	public void setDataHora(String dataHora) {
		this.dataHora = dataHora;
	}

	@Override
	public String toString() {
		return "Cotacao [cia=" + cia + ", dtIda=" + dtIda + ", dtVolta=" + dtVolta + ", valor=" + valor + ", dataHora="
				+ dataHora + "]";
	}

}
