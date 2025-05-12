# 💸 SplitWise-Clone: Mobile Computing Course Project

This project is a **modified version of the SplitWise Android application**, developed as part of the *Mobile Computing* course. It enables users to efficiently track shared expenses and settle balances within groups such as friends, families, or roommates.

## 📱 Features

- Create groups and add shared expenses  
- View individual and group-wise balances  
- Real-time synchronization across devices  
- Offline support with seamless data consistency  
- Push notifications for updates and actions  

## 🛠️ Tech Stack

- **UI**: Jetpack Compose for modern, declarative UI  
- **Local Storage**: Room Database for caching Firebase data  
- **Cloud Backend**: Firebase Firestore for real-time data storage  
- **Sync Mechanism**: Combined Room + Firestore approach ensures:  
  - Offline availability  
  - Live updates on shared data  
  - Reduced redundant network usage via local caching  
- **Android Components**: Use of `Intent`, background `Services`, and `Notifications` for inter-app communication  
- **Error Handling**: Graceful handling of network/database failures for a robust user experience  

## 🎥 Presentation

👉 [View Presentation on Figma](https://www.figma.com/deck/TprYDhHzWpXbtwxskYHeaN/MC-Project-Presentation?node-id=1-32&t=u0SZWqGHzTFDQyjd-1)

---


