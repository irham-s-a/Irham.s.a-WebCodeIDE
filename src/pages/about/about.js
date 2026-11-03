import "./about.scss";
import Logo from "components/logo";
import Page from "components/page";
import Reactive from "html-tag-js/reactive";
import actionStack from "lib/actionStack";

/**
 * Irham.s.a WebCode IDE - data pengembang (v0.3.0).
 * Ubah nilai di sini untuk memperbarui halaman About.
 * Kolom `email`, `github`, dan `website` bersifat opsional: kalau dikosongkan,
 * barisnya tidak ditampilkan. Tidak ada koneksi internet yang dibutuhkan
 * kecuali pengguna sendiri menekan tautan tersebut.
 */
const DEVELOPER = {
	name: "Irham Satria Alfitra",
	location: "Bogor, Indonesia",
	role: "Fullstack Web Developer & AI Learner",
	focus: "Pengembang aplikasi Android dan alat otomasi",
	email: "",
	github: "",
	website: "",
};

const APP_NAME = "Irham.s.a WebCode IDE";

/**
 * Satu baris info (ikon + judul + subteks), dengan klik opsional.
 * @param {{icon:string,title:string,subtitle?:string,onclick?:()=>void}} props
 */
function InfoItem({ icon, title, subtitle, onclick }) {
	return (
		<div
			className="info-item"
			style={onclick ? { cursor: "pointer" } : undefined}
			onclick={onclick}
		>
			<div className="info-item-icon">
				<span className={`icon ${icon}`}></span>
			</div>
			<div className="info-item-text">
				{title}
				{subtitle ? <div className="info-item-subtext">{subtitle}</div> : null}
			</div>
		</div>
	);
}

export default function AboutInclude() {
	const $page = Page(strings.about.capitalize());
	const webviewVersionName = Reactive("N/A");
	const webviewPackageName = Reactive("N/A");

	const developerItems = [
		<InfoItem icon="person" title={DEVELOPER.name} subtitle="Pengembang" />,
		<InfoItem icon="code" title={DEVELOPER.role} subtitle="Peran" />,
		<InfoItem icon="android" title={DEVELOPER.focus} subtitle="Bidang karya" />,
		<InfoItem icon="info" title={DEVELOPER.location} subtitle="Lokasi" />,
	];

	if (DEVELOPER.email) {
		developerItems.push(
			<InfoItem
				icon="gmail"
				title="Email"
				subtitle={DEVELOPER.email}
				onclick={() => system.openInBrowser(`mailto:${DEVELOPER.email}`)}
			/>,
		);
	}
	if (DEVELOPER.github) {
		developerItems.push(
			<InfoItem
				icon="github"
				title="GitHub"
				subtitle={DEVELOPER.github}
				onclick={() => system.openInBrowser(DEVELOPER.github)}
			/>,
		);
	}
	if (DEVELOPER.website) {
		developerItems.push(
			<InfoItem
				icon="public"
				title="Website"
				subtitle={DEVELOPER.website}
				onclick={() => system.openInBrowser(DEVELOPER.website)}
			/>,
		);
	}

	$page.classList.add("about-us");
	$page.body = (
		<main id="about-page" className="main scroll">
			<Logo />

			<div className="version-info">
				<h1 className="version-title">{APP_NAME}</h1>
				<div className="version-number">
					Versi {BuildInfo.version} ({BuildInfo.versionCode})
				</div>
			</div>

			<div className="info-section">{developerItems}</div>

			<div className="info-section">
				<div className="info-item">
					<div className="info-item-icon">
						<span className="icon googlechrome"></span>
					</div>
					<div className="info-item-text">
						Webview {webviewVersionName}
						<div className="info-item-subtext">{webviewPackageName}</div>
					</div>
				</div>
				<InfoItem
					icon="phone_android"
					title="Paket"
					subtitle={BuildInfo.packageName}
				/>
				<InfoItem
					icon="acode"
					title="Dibuat dari Acode (lisensi MIT)"
					subtitle="Hak cipta Acode © Foxdebug (Ajit Kumar)"
				/>
			</div>
		</main>
	);

	system.getWebviewInfo((res) => {
		webviewPackageName.value = res?.packageName || "N/A";
		webviewVersionName.value = res?.versionName || "N/A";
	});

	actionStack.push({
		id: "about",
		action: $page.hide,
	});

	$page.onhide = function () {
		actionStack.remove("about");
	};

	app.append($page);
}
