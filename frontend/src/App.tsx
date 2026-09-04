import { Routes, Route } from 'react-router-dom'
import { ProductList } from './components/ProductList'
import './App.css'

function App() {
  return (
    <main>
      <Routes>
        <Route path="/" element={<ProductList />} />
      </Routes>
    </main>
  )
}

export default App
