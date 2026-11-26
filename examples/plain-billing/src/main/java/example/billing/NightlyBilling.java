package example.billing;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

// The nightly billing run, as old batch jobs are written: it saves each invoice to the shared invoices folder,
// prints it, and mails it through the company's mail server.
public class NightlyBilling {

    static final File INVOICES = new File("/airgap-example-invoices.invalid");
    static final String MAIL_SERVER = "mail.example.invalid";

    public void bill(final String customer) throws IOException {
        final File invoice = new File(INVOICES, customer + ".txt");
        try (OutputStream out = new FileOutputStream(invoice)) {
            out.write(("Invoice for " + customer + "\n").getBytes("UTF-8"));
        }
    }

    public void print(final String customer) throws IOException {
        new ProcessBuilder("lpr", new File(INVOICES, customer + ".txt").getPath()).start();
    }

    public void mail(final String customer) throws IOException {
        try (Socket smtp = new Socket(MAIL_SERVER, 25)) {
            smtp.getOutputStream().write(("HELO billing\r\nRCPT TO:<" + customer + ">\r\n").getBytes("UTF-8"));
        }
    }
}
