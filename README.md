# Redmi 12 için Bulut Not Uygulaması

Bu proje, telefonundan (Redmi 12 dahil) not alıp bilgisayarında çalışan bir sunucuya kaydetmeni sağlar.

## Özellikler
- Email + şifre ile kayıt/giriş
- Not ekleme, düzenleme, silme
- Notları bilgisayarda `data/db.json` içinde saklama
- Aynı hesaba giriş yapınca her cihazdan notları görme

## Kurulum
```bash
npm install
npm start
```

Uygulama: `http://localhost:3000`

## Telefonda Kullanım (Aynı Wi-Fi)
Bilgisayar IP adresini öğren:
```bash
hostname -I
```
Örnek: `192.168.1.35`

Telefonda şu adresi aç:
`http://192.168.1.35:3000`

## Her Yerden Erişim (İnternet)
Bilgisayarındaki uygulamayı internetten erişilebilir yapmak için:

### Seçenek 1: Cloudflare Tunnel (önerilir)
1. Cloudflare hesabı oluştur
2. `cloudflared` kur
3. Tünel açıp `localhost:3000`'e yönlendir

### Seçenek 2: ngrok
```bash
ngrok http 3000
```
Çıkan `https://...` linkini telefondan aç.

## Güvenlik Notu
`server.js` içinde yer alan `JWT_SECRET` değerini güçlü bir şifre ile değiştir ve üretimde `.env` ile yönet.
