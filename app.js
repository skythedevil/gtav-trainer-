const addressInput = document.querySelector("#ps5-address");
const openButton = document.querySelector("#open-ps5");
const connectionMessage = document.querySelector("#connection-message");
const moduleMessage = document.querySelector("#module-message");

try { addressInput.value = localStorage.getItem("gtav-ps5-address") || ""; } catch {}

function normalizedAddress(value) {
  const raw = value.trim().replace(/^https?:\/\//i, "").replace(/\/$/, "");
  if (!raw || /[\s/]/.test(raw)) return null;
  return raw;
}

openButton.addEventListener("click", () => {
  const address = normalizedAddress(addressInput.value);
  if (!address) {
    connectionMessage.textContent = "Enter a valid local IP address or host, for example 192.168.1.100.";
    addressInput.focus();
    return;
  }
  try { localStorage.setItem("gtav-ps5-address", address); } catch {}
  connectionMessage.textContent = "Opening http://" + address + " in a new tab. A page opening does not confirm PS5Debug-NG is available.";
  window.open("http://" + address, "_blank", "noopener,noreferrer");
});

document.querySelectorAll(".module").forEach((button) => {
  button.addEventListener("click", () => {
    const name = button.dataset.module;
    moduleMessage.textContent = name + " selected. Cheat controls become available after the PS5Debug-NG installer/payload bridge is integrated.";
    document.querySelectorAll(".module").forEach((item) => item.setAttribute("aria-pressed", String(item === button)));
  });
});

document.querySelector("#install-info").addEventListener("click", () => {
  document.querySelector(".install-panel").scrollIntoView({behavior:"smooth", block:"center"});
  connectionMessage.textContent = "Next step: integrate and test a compatible PS5Debug-NG bridge for your exact console firmware and GTA V build.";
});

document.querySelectorAll("[data-scroll]").forEach((link) => {
  link.addEventListener("click", (event) => {
    event.preventDefault();
    const target = document.querySelector("." + link.dataset.scroll);
    if (target) target.scrollIntoView({behavior:"smooth", block:"start"});
  });
});

if ("serviceWorker" in navigator && location.protocol !== "file:") {
  window.addEventListener("load", () => navigator.serviceWorker.register("/sw.js").catch(() => {}));
}