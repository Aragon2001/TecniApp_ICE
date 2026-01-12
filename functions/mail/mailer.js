const nodemailer = require("nodemailer");

const transporter = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: process.env.MAIL_USER || require("firebase-functions").config().mail.user,
    pass: process.env.MAIL_PASS || require("firebase-functions").config().mail.pass,
  },
});

module.exports = async function sendMail({ to, subject, html }) {
  await transporter.sendMail({
    from: '"TecniApp ICE" <tecniappice@gmail.com>',
    to,
    subject,
    html,
  });
};
