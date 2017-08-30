package model;

public class Flight {
	private String cia;
	private float valor;
	private String msg;

	public String getCia() {
		return cia;
	}

	public void setCia(String cia) {
		this.cia = cia;
	}

	public float getValor() {
		return valor;
	}

	public void setValor(float valor) {
		this.valor = valor;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	@Override
	public String toString() {
		return "Flight [cia=" + cia + ", valor=" + valor + ", msg=" + msg + "]";
	}

}
